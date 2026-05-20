/*
 * DiscordSRV - https://github.com/DiscordSRV/DiscordSRV
 *
 * Copyright (C) 2016 - 2024 Austin "Scarsz" Shapiro
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 */

package github.scarsz.discordsrv.objects.log4j;

import github.scarsz.discordsrv.Debug;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.util.DiscordUtil;
import github.scarsz.discordsrv.util.MessageUtil;
import github.scarsz.discordsrv.util.PlaceholderUtil;
import github.scarsz.discordsrv.util.SchedulerUtil;
import github.scarsz.discordsrv.util.TimeUtil;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.bukkit.plugin.Plugin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Replaces the JDA-4-only {@code me.scarsz.jdaappender} library after the JDA 6 migration.
 *
 * <p>Wraps a log4j2 root appender that buffers log events under a {@link ReentrantLock} and flushes
 * them to a Discord {@link TextChannel} on a scheduled cadence via {@link SchedulerUtil}. The lock
 * is virtual-thread-friendly (no carrier-thread pinning on Java 21) and the flush task runs through
 * Folia's async scheduler when present.</p>
 *
 * <p>Behavior intentionally matches jdaappender for backwards compatibility with existing config:
 * level filtering, logger-name mapping, prefix/suffix templating, optional code-block wrapping, and
 * 2000-character message splitting.</p>
 */
public class ConsoleChannelAppender extends AbstractAppender {

    /** Soft cap on buffered log lines; oldest is dropped on overflow so we never OOM the server. */
    private static final int MAX_BUFFER = 2000;
    /** Flush cadence in ticks (1 tick ≈ 50 ms). 30 ticks ≈ 1.5 s — same feel as jdaappender. */
    private static final long FLUSH_PERIOD_TICKS = 30L;
    /** Discord hard limit on message content; we leave a few chars of slack for the code block wrapper. */
    private static final int DISCORD_LIMIT = 2000;

    private final Supplier<TextChannel> channelSupplier;
    private final Deque<String> buffer = new ArrayDeque<>(MAX_BUFFER);
    private final ReentrantLock bufferLock = new ReentrantLock();
    private volatile boolean scheduled = false;

    public ConsoleChannelAppender(Supplier<TextChannel> channelSupplier) {
        super("DiscordSRV-ConsoleChannelAppender", null, null, true, Property.EMPTY_ARRAY);
        this.channelSupplier = channelSupplier;
    }

    @Override
    public void append(LogEvent event) {
        if (!shouldForward(event)) return;
        String line = format(event);
        if (line == null || line.isEmpty()) return;
        bufferLock.lock();
        try {
            // ring-buffer semantics: drop oldest if cap reached
            while (buffer.size() >= MAX_BUFFER) buffer.pollFirst();
            buffer.addLast(line);
        } finally {
            bufferLock.unlock();
        }
    }

    /**
     * Schedule periodic flushes. Idempotent — calling more than once is a no-op.
     * Must be called after this appender has been attached to the root logger.
     */
    public void scheduleFlush(Plugin plugin) {
        if (scheduled) return;
        scheduled = true;
        SchedulerUtil.runTaskTimerAsynchronously(plugin, this::flush, FLUSH_PERIOD_TICKS, FLUSH_PERIOD_TICKS);
    }

    /**
     * Detach from the root logger, do a final flush, then stop the appender.
     * Safe to call from {@code onDisable} regardless of scheduler state.
     */
    public void shutdown() {
        try {
            ((org.apache.logging.log4j.core.Logger) LogManager.getRootLogger()).removeAppender(this);
        } catch (Throwable ignored) {
            // appender may not have been attached; safe to ignore
        }
        try {
            flush();
        } catch (Throwable t) {
            DiscordSRV.debug(Debug.UNCATEGORIZED, "Final console forwarder flush failed: " + t.getMessage());
        }
        super.stop();
    }

    private void flush() {
        TextChannel channel = channelSupplier.get();
        if (channel == null) return;

        List<String> drained;
        bufferLock.lock();
        try {
            if (buffer.isEmpty()) return;
            drained = new ArrayList<>(buffer);
            buffer.clear();
        } finally {
            bufferLock.unlock();
        }

        boolean useCodeBlocks = DiscordSRV.config().getBooleanElse("DiscordConsoleChannelUseCodeBlocks", true);
        // wrapper accounts for "```\n" prefix + "\n```" suffix added by send(...)
        int wrapperOverhead = useCodeBlocks ? 8 : 0;
        int limit = DISCORD_LIMIT - wrapperOverhead;

        StringBuilder current = new StringBuilder();
        for (String line : drained) {
            // a single line that's longer than the whole limit gets truncated rather than dropped
            if (line.length() > limit) line = line.substring(0, limit - 3) + "...";

            if (current.length() + 1 + line.length() > limit) {
                send(channel, current.toString(), useCodeBlocks);
                current.setLength(0);
            }
            if (current.length() > 0) current.append('\n');
            current.append(line);
        }
        if (current.length() > 0) {
            send(channel, current.toString(), useCodeBlocks);
        }
    }

    private void send(TextChannel channel, String content, boolean useCodeBlocks) {
        String formatted = useCodeBlocks ? "```\n" + content + "\n```" : content;
        try {
            channel.sendMessage(MessageCreateData.fromContent(formatted)).queue(
                    null,
                    err -> DiscordSRV.debug(Debug.UNCATEGORIZED, "Console forwarder send failed: " + err.getMessage())
            );
        } catch (Throwable t) {
            DiscordSRV.debug(Debug.UNCATEGORIZED, "Console forwarder send threw: " + t.getMessage());
        }
    }

    private boolean shouldForward(LogEvent event) {
        List<String> levels = DiscordSRV.config().getStringList("DiscordConsoleChannelLevels");
        if (levels == null || levels.isEmpty()) return true; // empty list = forward all levels
        String levelName = event.getLevel().name();
        for (String allowed : levels) {
            if (allowed != null && allowed.equalsIgnoreCase(levelName)) return true;
        }
        return false;
    }

    private String format(LogEvent event) {
        int padding = DiscordSRV.config().getIntElse("DiscordConsoleChannelPadding", 0);
        String name = padding > 0 ? pad(mapLoggerName(event.getLoggerName()), padding) : mapLoggerName(event.getLoggerName());
        String timestamp = TimeUtil.consoleTimeStamp(event.getInstant().getEpochMillisecond());
        String level = event.getLevel().name();
        String message = event.getMessage() != null ? event.getMessage().getFormattedMessage() : "";

        String prefix = applyTemplate(DiscordSRV.config().getString("DiscordConsoleChannelPrefix"), timestamp, name, level);
        String suffix = applyTemplate(DiscordSRV.config().getString("DiscordConsoleChannelSuffix"), timestamp, name, level);

        StringBuilder builder = new StringBuilder();
        builder.append(prefix).append(message).append(suffix);
        if (event.getThrown() != null) {
            builder.append('\n').append(ExceptionUtils.getStackTrace(event.getThrown()));
        }

        String line = MessageUtil.strip(DiscordUtil.aggressiveStrip(builder.toString()));

        // apply user-configured regex filters from DiscordConsoleChannelFilters
        Map<Pattern, String> regexes = DiscordSRV.getPlugin().getConsoleRegexes();
        if (regexes != null) {
            for (Map.Entry<Pattern, String> entry : regexes.entrySet()) {
                line = entry.getKey().matcher(line).replaceAll(entry.getValue());
                if (line.isEmpty()) return null;
            }
        }
        return line;
    }

    private String applyTemplate(String template, String timestamp, String name, String level) {
        if (template == null || template.isEmpty()) return "";
        return PlaceholderUtil.replacePlaceholdersToDiscord(template)
                .replace("{date}", timestamp)
                .replace("{datetime}", timestamp)
                .replace("{name}", name == null || name.isEmpty() ? "" : " " + name)
                .replace("{level}", level);
    }

    /**
     * Maps long Mojang/JDA logger names to short, readable equivalents — same scheme as jdaappender.
     */
    private String mapLoggerName(String rawName) {
        if (rawName == null) return "";
        if (rawName.equals("net.minecraft.server.MinecraftServer")) return "Server";
        if (rawName.startsWith("net.minecraft.server.")) return "Server/" + rawName.substring("net.minecraft.server.".length());
        if (rawName.startsWith("net.minecraft.")) return "Minecraft/" + rawName.substring("net.minecraft.".length());
        if (rawName.startsWith("github.scarsz.discordsrv.dependencies.jda.")) return "DiscordSRV/JDA/" + rawName.substring("github.scarsz.discordsrv.dependencies.jda.".length());
        return rawName;
    }

    private static String pad(String s, int width) {
        if (s == null) return " ".repeat(width);
        if (s.length() >= width) return s;
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) sb.append(' ');
        return sb.toString();
    }
}
