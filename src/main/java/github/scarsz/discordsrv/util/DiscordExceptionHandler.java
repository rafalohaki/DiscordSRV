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

package github.scarsz.discordsrv.util;

import github.scarsz.discordsrv.DiscordSRV;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.exceptions.PermissionException;

/**
 * Centralized handler for JDA {@link PermissionException} — extracted from
 * 5+ duplicated try-catch blocks across {@link DiscordUtil} (sendMessage,
 * queueMessage, setTextChannelTopic, deleteMessage, role management).
 *
 * <p>Usage:
 * <pre>{@code
 * try {
 *     channel.sendMessage(message).queue();
 * } catch (PermissionException e) {
 *     DiscordExceptionHandler.handlePermission("send message in " + channel, e);
 * } catch (IllegalStateException e) {
 *     DiscordExceptionHandler.handleIllegalState("send message in " + channel, e);
 * }
 * }</pre>
 *
 * <p>Java 25 switch expression for the permission-vs-unknown branch.
 */
public final class DiscordExceptionHandler {

    private DiscordExceptionHandler() {}

    /**
     * Logs a permission exception with a human-readable message.
     *
     * @param operation description of what was attempted (e.g. "send message in #general")
     * @param e         the JDA permission exception
     */
    public static void handlePermission(String operation, PermissionException e) {
        String reason = switch (e.getPermission()) {
            case Permission.UNKNOWN -> "\"" + e.getMessage() + "\"";
            default -> "missing permission \"" + e.getPermission().getName() + "\"";
        };
        DiscordSRV.warning("Could not " + operation + " because " + reason);
    }

    /**
     * Logs an illegal-state exception from a JDA action.
     *
     * @param operation description of what was attempted
     * @param e         the illegal state exception
     */
    public static void handleIllegalState(String operation, IllegalStateException e) {
        DiscordSRV.error("Could not " + operation + ": " + e.getMessage());
    }

    /**
     * Convenience: wraps both {@link #handlePermission} and {@link #handleIllegalState}
     * for the common pattern in {@code DiscordUtil} message-sending methods.
     *
     * @param operation description of what was attempted
     * @param e         the thrown exception (PermissionException or IllegalStateException)
     */
    public static void handle(String operation, RuntimeException e) {
        if (e instanceof PermissionException pe) {
            handlePermission(operation, pe);
        } else if (e instanceof IllegalStateException ise) {
            handleIllegalState(operation, ise);
        } else {
            DiscordSRV.error("Could not " + operation + ": " + e.getMessage());
        }
    }
}
