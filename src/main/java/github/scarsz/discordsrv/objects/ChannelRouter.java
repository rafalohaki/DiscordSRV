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

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.util.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * Routes messages between Minecraft game channels and Discord text channels.
 *
 * <p>The {@code channels} map (game channel name → Discord channel ID) is the
 * single source of truth. This class centralizes all lookups so that channel
 * resolution bugs (case sensitivity, null JDA, missing channel) are easy to
 * find and fix.
 */
public final class ChannelRouter {

    private final DiscordSRV plugin;
    private final Map<String, String> channels;

    public ChannelRouter(DiscordSRV plugin, Map<String, String> channels) {
        this.plugin = plugin;
        this.channels = channels;
    }

    public String getMainChatChannel() {
        return channels.isEmpty() ? null : channels.keySet().iterator().next();
    }

    public TextChannel getMainTextChannel() {
        if (channels.isEmpty() || plugin.getJda() == null) return null;
        String firstChannel = channels.values().iterator().next();
        if (StringUtils.isBlank(firstChannel)) return null;
        return DiscordUtil.getTextChannelById(firstChannel);
    }

    public Guild getMainGuild() {
        if (plugin.getJda() == null) return null;
        return getMainTextChannel() != null
                ? getMainTextChannel().getGuild()
                : getConsoleChannel() != null
                        ? getConsoleChannel().getGuild()
                        : plugin.getJda().getGuilds().size() > 0
                                ? plugin.getJda().getGuilds().get(0)
                                : null;
    }

    public TextChannel getConsoleChannel() {
        if (plugin.getJda() == null) return null;
        String consoleChannel = DiscordSRV.config().getString("DiscordConsoleChannelId");
        return StringUtils.isNotBlank(consoleChannel) && StringUtils.isNumeric(consoleChannel)
                ? DiscordUtil.getTextChannelById(consoleChannel)
                : null;
    }

    public TextChannel getDestinationTextChannelForGameChannelName(String gameChannelName) {
        Map.Entry<String, String> entry = channels.entrySet().stream()
                .filter(e -> e.getKey().equals(gameChannelName))
                .findFirst().orElse(null);
        String value = entry != null ? entry.getValue() : null;
        if (!StringUtils.isBlank(value)) {
            return plugin.getJda().getTextChannelById(value);
        }
        // no case-sensitive channel found, try case-insensitive
        entry = channels.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(gameChannelName))
                .findFirst().orElse(null);
        value = entry != null ? entry.getValue() : null;
        if (!StringUtils.isBlank(value)) {
            return plugin.getJda().getTextChannelById(value);
        }
        return null;
    }

    public String getDestinationGameChannelNameForTextChannel(TextChannel source) {
        if (source == null) return null;
        return channels.entrySet().stream()
                .filter(entry -> source.getId().equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
    }

    public String getOptionalChannel(String name) {
        return channels.containsKey(name)
                ? name
                : getMainChatChannel();
    }

    public TextChannel getOptionalTextChannel(String gameChannel) {
        return getDestinationTextChannelForGameChannelName(getOptionalChannel(gameChannel));
    }
}
