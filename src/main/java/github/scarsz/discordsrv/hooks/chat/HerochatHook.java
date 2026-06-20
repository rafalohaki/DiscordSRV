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

import com.dthielke.herochat.Channel;
import com.dthielke.herochat.ChannelChatEvent;
import com.dthielke.herochat.Chatter;
import com.dthielke.herochat.Herochat;
import github.scarsz.discordsrv.Debug;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.util.PluginUtil;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

public class HerochatHook implements ChatHook {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMessage(ChannelChatEvent event) {
        if (StringUtils.isBlank(event.getMessage())) return;

        DiscordSRV.getPlugin().processChatMessage(event.getSender().getPlayer(), event.getMessage(), event.getChannel().getName(), event.getResult() != Chatter.Result.ALLOWED, event);
    }

    @Override
    public @Nullable ChannelInfo resolveChannel(String channelName) {
        Channel chatChannel = getChannelByCaseInsensitiveName(channelName);
        if (chatChannel == null) return null;
        return new ChannelInfo(
                chatChannel.getName(),
                chatChannel.getNick(),
                chatChannel.getColor().toString(),
                chatChannel.getMembers().stream()
                        .map(Chatter::getPlayer)
                        .collect(Collectors.toList())
        );
    }

    @Override
    public void deliverToRecipients(java.util.Collection<? extends Player> recipients, String formattedMessage) {
        // Herochat has its own send API — but the default per-player delivery
        // via getScheduler().run() is more Folia-safe than sendRawMessage.
        // Override to use sendRawMessage on the channel for native formatting.
        ChatHook.super.deliverToRecipients(recipients, formattedMessage);
    }

    private static Channel getChannelByCaseInsensitiveName(String name) {
        List<Channel> channels = Herochat.getChannelManager().getChannels();

        if (channels.size() > 0) {
            for (Channel channel : Herochat.getChannelManager().getChannels()) {
                DiscordSRV.debug(Debug.DISCORD_TO_MINECRAFT, "\"" + channel.getName() + "\" equalsIgnoreCase \"" + name + "\" == " + channel.getName().equalsIgnoreCase(name));
                if (channel.getName().equalsIgnoreCase(name)) {
                    return channel;
                }
            }
            DiscordSRV.debug(Debug.DISCORD_TO_MINECRAFT, "No matching Herochat channels for name \"" + name + "\"");
        } else {
            DiscordSRV.debug(Debug.DISCORD_TO_MINECRAFT, "Herochat's channel manager returned no registered channels");
        }
        return null;
    }

    @Override
    public Plugin getPlugin() {
        return PluginUtil.getPlugin("Herochat");
    }

}
