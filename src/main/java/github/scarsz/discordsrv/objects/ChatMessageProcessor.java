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
import github.scarsz.discordsrv.api.events.GameChatMessagePostProcessEvent;
import github.scarsz.discordsrv.api.events.GameChatMessagePreProcessEvent;
import github.scarsz.discordsrv.hooks.VaultHook;
import github.scarsz.discordsrv.util.DiscordUtil;
import github.scarsz.discordsrv.util.GamePermissionUtil;
import github.scarsz.discordsrv.util.LangUtil;
import github.scarsz.discordsrv.util.MessageUtil;
import github.scarsz.discordsrv.util.PlaceholderUtil;
import github.scarsz.discordsrv.util.PluginUtil;
import github.scarsz.discordsrv.util.TimeUtil;
import github.scarsz.discordsrv.util.WebhookUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Processes Minecraft → Discord chat messages.
 *
 * <p>Extracted from {@link DiscordSRV} to isolate the complex pipeline:
 * permission checks → mcMMO filter → prefix blacklist/whitelist → event
 * firing → placeholder translation → regex filtering → webhook or regular
 * delivery. This was the single most complex method in the god class and a
 * frequent source of bugs.
 */
public final class ChatMessageProcessor {

    private final DiscordSRV plugin;

    public ChatMessageProcessor(DiscordSRV plugin) {
        this.plugin = plugin;
    }

    public void processChatMessage(Player player, String message, String channel, boolean cancelled) {
        processChatMessage(player, message, channel, cancelled, null);
    }

    public void processChatMessage(Player player, String message, String channel, boolean cancelled, org.bukkit.event.Event event) {
        processChatMessage(player, MessageUtil.toComponent(message, true), channel, cancelled, event);
    }

    @Deprecated
    public void processChatMessage(Player player, Component message, String channel, boolean cancelled) {
        processChatMessage(player, message, channel, cancelled, null);
    }

    @SuppressWarnings("deprecation") // Display names are legacy, Spigot is supported
    public void processChatMessage(Player player, Component message, String channel, boolean cancelled, org.bukkit.event.Event event) {
        DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Chat message received, canceled: " + cancelled + ", channel: " + channel);

        if (player == null) {
            DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Received chat message was from a null sender, not processing message");
            return;
        }

        if (!GamePermissionUtil.hasPermission(player, "discordsrv.chat")) {
            DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "User " + player.getName() + " sent a message but it was not delivered to Discord due to lack of in-game permission (discordsrv.chat)");
            return;
        }

        if (PluginUtil.pluginHookIsEnabled("mcMMO")) {
            if (player.hasMetadata("mcMMO: Player Data")) {
                boolean usingAdminChat = com.gmail.nossr50.api.ChatAPI.isUsingAdminChat(player);
                boolean usingPartyChat = com.gmail.nossr50.api.ChatAPI.isUsingPartyChat(player);
                if (usingAdminChat || usingPartyChat) {
                    DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Not processing message because message was from " + (usingAdminChat ? "admin" : "party") + " chat");
                    return;
                }
            }
        }

        if (DiscordSRV.config().getBooleanElse("RespectChatPlugins", true) && cancelled) {
            DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "User " + player.getName() + " sent a message but it was not delivered to Discord because the chat event was canceled");
            return;
        }

        if (!DiscordSRV.config().getBoolean("DiscordChatChannelMinecraftToDiscord")) {
            DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "User " + player.getName() + " sent a message but it was not delivered to Discord because DiscordChatChannelMinecraftToDiscord is false");
            return;
        }

        String prefix = DiscordSRV.config().getString("DiscordChatChannelPrefixRequiredToProcessMessage");
        boolean blacklist = DiscordSRV.config().getBoolean("DiscordChatChannelPrefixActsAsBlacklist");
        String legacy = MessageUtil.toLegacy(message);
        if (MessageUtil.strip(legacy).startsWith(prefix) == blacklist) {
            DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "User " + player.getName() + " sent a message but it was not delivered to Discord because " + (blacklist ? "the message started with \"" + prefix : "the message didn't start with \"" + prefix) + "\" (DiscordChatChannelPrefixRequiredToProcessMessage): \"" + message + "\"");
            return;
        }

        GameChatMessagePreProcessEvent preEvent = DiscordSRV.api.callEvent(new GameChatMessagePreProcessEvent(channel, message, player, event));
        if (preEvent.isCancelled()) {
            DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "GameChatMessagePreProcessEvent was cancelled, message send aborted");
            return;
        }
        channel = preEvent.getChannel();
        message = preEvent.getMessageComponent();

        String userPrimaryGroup = VaultHook.getPrimaryGroup(player);
        boolean hasGoodGroup = StringUtils.isNotBlank(userPrimaryGroup);
        if (hasGoodGroup) userPrimaryGroup = userPrimaryGroup.substring(0, 1).toUpperCase() + userPrimaryGroup.substring(1);

        boolean reserializer = DiscordSRV.config().getBoolean("Experiment_MCDiscordReserializer_ToDiscord");
        boolean webhookMessageDelivery = DiscordSRV.config().getBoolean("Experiment_WebhookChatMessageDelivery");

        String discordMessageContent;
        if (reserializer) {
            discordMessageContent = MessageUtil.reserializeToDiscord(message);
        } else {
            discordMessageContent = MessageUtil.strip(MessageUtil.toLegacy(message));
        }

        if (webhookMessageDelivery) {
            discordMessageContent = processRegex(discordMessageContent);
            if (discordMessageContent == null) return;
        }

        if (DiscordSRV.config().getBoolean("DiscordChatChannelTranslateMentions")) {
            discordMessageContent = DiscordUtil.convertMentionsFromNames(discordMessageContent, plugin.getMainGuild());
        } else {
            discordMessageContent = discordMessageContent.replace("@", "@\u200B");
        }

        String processedMessage = discordMessageContent;

        if (!webhookMessageDelivery) {
            String username = player.getName();
            if (!reserializer) username = DiscordUtil.escapeMarkdown(username);
            String displayName = MessageUtil.strip(player.getDisplayName());

            String discordMessagePattern = (hasGoodGroup
                    ? LangUtil.Message.CHAT_TO_DISCORD.toString()
                    : LangUtil.Message.CHAT_TO_DISCORD_NO_PRIMARY_GROUP.toString())
                    .replace("%displayname%", DiscordUtil.escapeMarkdown(displayName))
                    .replace("%displaynamenoescapes%", displayName)
                    .replace("%username%", username)
                    .replaceAll("%time%|%date%", TimeUtil.timeStamp())
                    .replace("%channelname%", channel != null ? channel.substring(0, 1).toUpperCase() + channel.substring(1) : "")
                    .replace("%primarygroup%", userPrimaryGroup)
                    .replace("%usernamenoescapes%", MessageUtil.strip(player.getName()))
                    .replace("%world%", player.getWorld().getName())
                    .replace("%worldalias%", MessageUtil.strip(plugin.getWorldAlias(player.getWorld().getName())));
            discordMessagePattern = PlaceholderUtil.replacePlaceholdersToDiscord(discordMessagePattern, player);

            if (!reserializer) {
                discordMessagePattern = MessageUtil.strip(discordMessagePattern);
            }

            discordMessagePattern = discordMessagePattern.replace("%message%", discordMessageContent);
            discordMessagePattern = processRegex(discordMessagePattern);
            if (discordMessagePattern == null) return;

            processedMessage = discordMessagePattern;
        }

        GameChatMessagePostProcessEvent postEvent = DiscordSRV.api.callEvent(new GameChatMessagePostProcessEvent(channel, processedMessage, player, preEvent.isCancelled(), event));
        if (postEvent.isCancelled()) {
            DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "GameChatMessagePostProcessEvent was cancelled, message send aborted");
            return;
        }
        channel = postEvent.getChannel();
        processedMessage = postEvent.getProcessedMessage();

        if (channel == null) channel = plugin.getOptionalChannel("global");
        TextChannel destinationChannel = plugin.getDestinationTextChannelForGameChannelName(channel);

        if (!webhookMessageDelivery) {
            DiscordUtil.sendMessage(destinationChannel, processedMessage);
        } else {
            if (destinationChannel == null) {
                DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Failed to find Discord channel to forward message from game channel " + channel);
                return;
            }
            if (!DiscordUtil.checkPermission(destinationChannel, Permission.MANAGE_WEBHOOKS)) {
                DiscordSRV.error("Couldn't deliver chat message as webhook because the bot lacks the \"Manage Webhooks\" permission.");
                return;
            }
            WebhookUtil.deliverMessage(destinationChannel, player, processedMessage);
        }
    }

    private String processRegex(String discordMessage) {
        for (Map.Entry<Pattern, String> entry : plugin.getGameRegexes().entrySet()) {
            discordMessage = entry.getKey().matcher(discordMessage).replaceAll(Matcher.quoteReplacement(entry.getValue()));
            if (StringUtils.isBlank(discordMessage)) {
                DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Not processing Minecraft message because it was cleared by a filter: " + entry.getKey().pattern());
                return null;
            }
        }
        return discordMessage;
    }
}
