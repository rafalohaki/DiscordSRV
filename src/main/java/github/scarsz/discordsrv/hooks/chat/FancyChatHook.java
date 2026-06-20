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

import br.com.finalcraft.fancychat.api.FancyChatApi;
import br.com.finalcraft.fancychat.api.FancyChatSendChannelMessageEvent;
import br.com.finalcraft.fancychat.config.fancychat.FancyChannel;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.util.MessageUtil;
import github.scarsz.discordsrv.util.PluginUtil;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

public class FancyChatHook implements ChatHook {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMessage(FancyChatSendChannelMessageEvent event) {
        if (event.getChannel() == null) return;
        if (StringUtils.isBlank(event.getMessage())) return;

        Player sender = null;
        if (event.getSender() instanceof Player) sender = (Player) event.getSender();

        DiscordSRV.getPlugin().processChatMessage(sender, event.getMessage(), event.getChannel().getName(), false, event);
    }

    @Override
    public @Nullable ChannelInfo resolveChannel(String channelName) {
        FancyChannel fancyChannel = FancyChatApi.getChannel(channelName);
        if (fancyChannel == null) return null;
        return new ChannelInfo(
                fancyChannel.getName(),
                fancyChannel.getAlias(),
                "",
                fancyChannel.getPlayersOnThisChannel()
        );
    }

    @Override
    public void deliverToRecipients(java.util.Collection<? extends Player> recipients, String formattedMessage) {
        // FancyChat has its own send API — use it instead of per-player sendMessage
        // Resolve the channel from the first recipient's context is not reliable;
        // the default broadcastMessageToChannel already formatted the message,
        // so we fall back to the default per-player delivery.
        ChatHook.super.deliverToRecipients(recipients, formattedMessage);
    }

    @Override
    public Plugin getPlugin() {
        return PluginUtil.getPlugin("EverNifeFancyChat");
    }

}
