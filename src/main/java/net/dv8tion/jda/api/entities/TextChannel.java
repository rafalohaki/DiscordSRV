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

package net.dv8tion.jda.api.entities;

/**
 * Binary-compatibility shim for plugins (e.g. CMI) compiled against
 * DiscordSRV's JDA 4/5 API where {@code TextChannel} lived in
 * {@code net.dv8tion.jda.api.entities}.
 *
 * <p>In JDA 6, {@code TextChannel} moved to
 * {@code net.dv8tion.jda.api.entities.channel.concrete.TextChannel}.
 * After Shadow relocation, the old path becomes
 * {@code github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel}
 * and the new path becomes
 * {@code github.scarsz.discordsrv.dependencies.jda.api.entities.channel.concrete.TextChannel}.
 *
 * <p>This empty marker interface extends the JDA 6 TextChannel so that:
 * <ol>
 *   <li>The relocated class {@code ...jda.api.entities.TextChannel} exists
 *       in the shaded jar — satisfying {@code NoClassDefFoundError} checks.</li>
 *   <li>Event {@code getChannel()} methods can use covariant return types:
 *       they declare the legacy return type (this interface) while returning
 *       a proxy that delegates to the real JDA 6 TextChannel.</li>
 * </ol>
 *
 * <p>The proxy is created by
 * {@link github.scarsz.discordsrv.api.LegacyChannelProxy#wrap}.
 *
 * <p>This class is in the {@code net.dv8tion.jda} package so that Shadow's
 * {@code relocate("net.dv8tion.jda", "github.scarsz.discordsrv.dependencies.jda")}
 * rule relocates it to the path CMI expects at runtime.
 */
public interface TextChannel extends net.dv8tion.jda.api.entities.channel.concrete.TextChannel {
    // Empty — inherits all methods from JDA 6 TextChannel.
}
