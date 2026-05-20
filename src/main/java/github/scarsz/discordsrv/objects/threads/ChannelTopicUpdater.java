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

package github.scarsz.discordsrv.objects.threads;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.util.DiscordUtil;
import github.scarsz.discordsrv.util.LangUtil;
import github.scarsz.discordsrv.util.PlaceholderUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.PermissionException;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChannelTopicUpdater extends Thread {

    // Per-channel "last attempt failed" flag. Upstream PR #1837: once the bot logs "missing
    // MANAGE_CHANNEL" we stop spamming the console every cycle; the next successful update clears
    // the flag so a future regression will log again.
    private final AtomicBoolean chatTopicFailed = new AtomicBoolean(false);
    private final AtomicBoolean consoleTopicFailed = new AtomicBoolean(false);

    public ChannelTopicUpdater() {
        setName("DiscordSRV - Channel Topic Updater");
        // Daemon so this loop never holds back JVM shutdown; the outer plugin disable path also
        // calls interrupt() (DiscordSRV#onDisable around line 1417).
        setDaemon(true);
    }

    @Override
    public void run() {
        DiscordSRV.info("Channel topic updater started — first update runs immediately, then every ≥10 min " +
                "(Discord rate-limits channel topic edits to ~2 per 10 min per channel).");
        while (true) {
            int rate = DiscordSRV.config().getInt("ChannelTopicUpdaterRateInMinutes");
            if (rate < 10) rate = 10;

            if (DiscordUtil.getJda() != null) {
                tryUpdate("chat", DiscordSRV.getPlugin().getMainTextChannel(), LangUtil.Message.CHAT_CHANNEL_TOPIC, chatTopicFailed);
                tryUpdate("console", DiscordSRV.getPlugin().getConsoleChannel(), LangUtil.Message.CONSOLE_CHANNEL_TOPIC, consoleTopicFailed);
            } else {
                DiscordSRV.debug("Skipping channel topic update cycle, JDA was null");
            }

            try {
                Thread.sleep(TimeUnit.MINUTES.toMillis(rate));
            } catch (InterruptedException e) {
                DiscordSRV.debug("Broke from Channel Topic Updater thread: sleep interrupted");
                return;
            }
        }
    }

    /**
     * Pre-validates the topic update so silent skip paths (null channel, blank format) get a debug
     * log instead of disappearing — makes it possible to diagnose "topic never updates" without
     * patching the plugin. Async setTopic failures route through {@code failedFlag} so we log the
     * permission warning once per outage instead of once per cycle.
     */
    private void tryUpdate(String label, TextChannel channel, LangUtil.Message format, AtomicBoolean failedFlag) {
        if (channel == null) {
            DiscordSRV.debug("Topic updater: " + label + " channel is null — skipping (configure the matching channel in config.yml Channels)");
            return;
        }
        String topic = PlaceholderUtil.replaceChannelUpdaterPlaceholders(format.toString());
        if (StringUtils.isBlank(topic)) {
            DiscordSRV.debug("Topic updater: " + label + " channel topic format is blank — skipping (set "
                    + format.getKeyName() + " in messages.yml to enable)");
            return;
        }
        DiscordUtil.setTextChannelTopic(
                channel,
                topic,
                error -> {
                    if (error instanceof PermissionException) {
                        PermissionException pe = (PermissionException) error;
                        if (failedFlag.compareAndSet(false, true)) {
                            String permName = pe.getPermission() != Permission.UNKNOWN ? pe.getPermission().getName() : pe.getMessage();
                            DiscordSRV.warning("Topic updater: bot lacks permission \"" + permName + "\" on #"
                                    + channel.getName() + " — topic updates for that channel will be skipped silently until the permission is granted.");
                        }
                    } else {
                        // non-permission errors (rate limit, network, etc.) still go through the standard
                        // warning path on every occurrence — they're transient and worth seeing.
                        DiscordSRV.warning("Topic updater: failed to set #" + channel.getName() + ": "
                                + error.getClass().getSimpleName() + ": " + error.getMessage());
                    }
                },
                () -> {
                    // Recovery: permission restored mid-runtime. Reset the flag so a future
                    // regression will log again, and emit a one-shot info message.
                    if (failedFlag.compareAndSet(true, false)) {
                        DiscordSRV.info("Topic updater: #" + channel.getName() + " permission recovered, topic updates resumed.");
                    }
                }
        );
    }

}
