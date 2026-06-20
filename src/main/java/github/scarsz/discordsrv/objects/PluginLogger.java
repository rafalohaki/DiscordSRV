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

package github.scarsz.discordsrv.objects;

import github.scarsz.discordsrv.Debug;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.util.DebugUtil;
import github.scarsz.discordsrv.util.LangUtil;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * Centralized logging for DiscordSRV. Extracted from the god class to keep
 * {@link DiscordSRV} focused on lifecycle and orchestration.
 *
 * <p>All methods delegate to the plugin's SLF4J logger. Debug output is
 * gated by {@link Debug#isVisible()} so production logs stay clean.
 */
public final class PluginLogger {

    private PluginLogger() {}

    public static void logThrowable(Throwable throwable, Consumer<String> logger) {
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        for (String line : stringWriter.toString().split("\n")) logger.accept(line);
    }

    public static void info(LangUtil.InternalMessage message) {
        info(message.toString());
    }

    public static void info(String message) {
        DiscordSRV.getPlugin().getLogger().info(message);
    }

    public static void warning(LangUtil.InternalMessage message) {
        warning(message.toString());
    }

    public static void warning(String message) {
        DiscordSRV.getPlugin().getLogger().warning(message);
    }

    public static void error(LangUtil.InternalMessage message) {
        error(message.toString());
    }

    public static void error(String message) {
        DiscordSRV.getPlugin().getLogger().severe(message);
    }

    public static void error(Throwable throwable) {
        logThrowable(throwable, PluginLogger::error);
    }

    public static void error(String message, Throwable throwable) {
        error(message);
        error(throwable);
    }

    public static void debug(String message) {
        debug(Debug.UNCATEGORIZED, message);
    }

    public static void debug(Debug type, String message) {
        if (type.isVisible()) {
            DiscordSRV.getPlugin().getLogger().info("[" + type.name() + " DEBUG] " + message
                    + (Debug.CALLSTACKS.isVisible() ? "\n" + DebugUtil.getStackTrace() : ""));
        }
    }

    public static void debug(Throwable throwable) {
        debug(Debug.UNCATEGORIZED, throwable);
    }

    public static void debug(Debug type, Throwable throwable) {
        logThrowable(throwable, PluginLogger::debug);
    }

    public static void debug(Throwable throwable, String message) {
        debug(Debug.UNCATEGORIZED, throwable, message);
    }

    public static void debug(Debug type, Throwable throwable, String message) {
        debug(throwable);
        debug(message);
    }

    public static void debug(Collection<String> message) {
        message.forEach(PluginLogger::debug);
    }

    public static void debug(Debug type, Collection<String> message) {
        message.forEach(msg -> debug(type, msg));
    }
}
