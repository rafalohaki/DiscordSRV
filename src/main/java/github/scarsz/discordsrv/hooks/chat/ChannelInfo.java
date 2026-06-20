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

package github.scarsz.discordsrv.hooks.chat;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Resolved channel metadata for Discord → Minecraft broadcast.
 *
 * <p>Java 25 record — immutable value object returned by
 * {@link ChatHook#resolveChannel(String)} and consumed by the default
 * {@link ChatHook#broadcastMessageToChannel(String, net.kyori.adventure.text.Component)}.
 *
 * @param name        display name of the channel (e.g. "Global")
 * @param nickname    short tag / alias (e.g. "G"); may be {@code null}
 * @param color       channel color prefix as a string (e.g. "§a"); may be {@code null}
 * @param recipients  players who should receive the message; empty if none
 */
public record ChannelInfo(
        String name,
        @Nullable String nickname,
        @Nullable String color,
        Collection<? extends Player> recipients
) {

    /** Convenience: nickname or name if nickname is null. */
    public String nicknameOrName() {
        return nickname != null ? nickname : name;
    }

    /** Convenience: color or empty string if null. */
    public String colorOrEmpty() {
        return color != null ? color : "";
    }
}
