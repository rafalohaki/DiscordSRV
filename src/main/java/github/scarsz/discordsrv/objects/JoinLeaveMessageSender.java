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
import github.scarsz.discordsrv.objects.MessageFormat;
import github.scarsz.discordsrv.util.DiscordUtil;
import github.scarsz.discordsrv.util.MessageFormatResolver;
import github.scarsz.discordsrv.util.MessageUtil;
import github.scarsz.discordsrv.util.PlaceholderUtil;
import github.scarsz.discordsrv.util.TimeUtil;
import github.scarsz.discordsrv.util.WebhookUtil;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.entity.Player;

import java.util.function.BiFunction;

/**
 * Sends Minecraft join/leave messages to Discord.
 *
 * <p>Extracted from {@link DiscordSRV} to isolate the placeholder translation
 * and webhook-vs-regular-message delivery logic. Both join and leave share
 * the same translation pattern; the only difference is the config key and
 * whether the player has played before (join only).
 */
public final class JoinLeaveMessageSender {

    private JoinLeaveMessageSender() {}

    public static void sendJoinMessage(Player player, String joinMessage) {
        if (player == null) throw new IllegalArgumentException("player cannot be null");

        MessageFormat messageFormat = player.hasPlayedBefore()
                ? getMessageFromConfiguration("MinecraftPlayerJoinMessage")
                : getMessageFromConfiguration("MinecraftPlayerFirstJoinMessage");
        if (messageFormat == null || !messageFormat.isAnyContent()) {
            DiscordSRV.debug("Not sending join message due to it being disabled");
            return;
        }

        TextChannel textChannel = DiscordSRV.getPlugin().getOptionalTextChannel("join");
        if (textChannel == null) {
            DiscordSRV.debug("Not sending join message, text channel is null");
            return;
        }

        final String displayName = StringUtils.isNotBlank(player.getDisplayName()) ? MessageUtil.strip(player.getDisplayName()) : "";
        final String message = StringUtils.isNotBlank(joinMessage) ? joinMessage : "";
        final String name = player.getName();
        final String avatarUrl = AvatarUrlResolver.getAvatarUrl(player);
        final String botAvatarUrl = DiscordUtil.getJda().getSelfUser().getEffectiveAvatarUrl();
        String botName = DiscordSRV.getPlugin().getMainGuild() != null
                ? DiscordSRV.getPlugin().getMainGuild().getSelfMember().getEffectiveName()
                : DiscordUtil.getJda().getSelfUser().getName();

        BiFunction<String, Boolean, String> translator = (content, needsEscape) -> {
            if (content == null) return null;
            content = content
                    .replaceAll("%time%|%date%", TimeUtil.timeStamp())
                    .replace("%message%", MessageUtil.strip(needsEscape ? DiscordUtil.escapeMarkdown(message) : message))
                    .replace("%username%", needsEscape ? DiscordUtil.escapeMarkdown(name) : name)
                    .replace("%displayname%", needsEscape ? DiscordUtil.escapeMarkdown(displayName) : displayName)
                    .replace("%usernamenoescapes%", name)
                    .replace("%displaynamenoescapes%", displayName)
                    .replace("%embedavatarurl%", avatarUrl)
                    .replace("%botavatarurl%", botAvatarUrl)
                    .replace("%botname%", botName);
            content = DiscordUtil.translateEmotes(content, textChannel.getGuild());
            content = PlaceholderUtil.replacePlaceholdersToDiscord(content, player);
            return content;
        };

        deliverMessage(messageFormat, translator, textChannel);
    }

    public static void sendLeaveMessage(Player player, String quitMessage) {
        if (player == null) throw new IllegalArgumentException("player cannot be null");

        MessageFormat messageFormat = getMessageFromConfiguration("MinecraftPlayerLeaveMessage");
        if (messageFormat == null || !messageFormat.isAnyContent()) {
            DiscordSRV.debug("Not sending leave message due to it being disabled");
            return;
        }

        TextChannel textChannel = DiscordSRV.getPlugin().getOptionalTextChannel("leave");
        if (textChannel == null) {
            DiscordSRV.debug("Not sending quit message, text channel is null");
            return;
        }

        final String displayName = StringUtils.isNotBlank(player.getDisplayName()) ? MessageUtil.strip(player.getDisplayName()) : "";
        final String message = StringUtils.isNotBlank(quitMessage) ? quitMessage : "";
        final String name = player.getName();

        String avatarUrl = AvatarUrlResolver.getAvatarUrl(player);
        String botAvatarUrl = DiscordUtil.getJda().getSelfUser().getEffectiveAvatarUrl();
        String botName = DiscordSRV.getPlugin().getMainGuild() != null
                ? DiscordSRV.getPlugin().getMainGuild().getSelfMember().getEffectiveName()
                : DiscordUtil.getJda().getSelfUser().getName();

        BiFunction<String, Boolean, String> translator = (content, needsEscape) -> {
            if (content == null) return null;
            content = content
                    .replaceAll("%time%|%date%", TimeUtil.timeStamp())
                    .replace("%message%", MessageUtil.strip(needsEscape ? DiscordUtil.escapeMarkdown(message) : message))
                    .replace("%username%", MessageUtil.strip(needsEscape ? DiscordUtil.escapeMarkdown(name) : name))
                    .replace("%displayname%", needsEscape ? DiscordUtil.escapeMarkdown(displayName) : displayName)
                    .replace("%usernamenoescapes%", name)
                    .replace("%displaynamenoescapes%", displayName)
                    .replace("%embedavatarurl%", avatarUrl)
                    .replace("%botavatarurl%", botAvatarUrl)
                    .replace("%botname%", botName);
            content = DiscordUtil.translateEmotes(content, textChannel.getGuild());
            content = PlaceholderUtil.replacePlaceholdersToDiscord(content, player);
            return content;
        };

        deliverMessage(messageFormat, translator, textChannel);
    }

    private static MessageFormat getMessageFromConfiguration(String key) {
        return MessageFormatResolver.getMessageFromConfiguration(DiscordSRV.config(), key);
    }

    private static void deliverMessage(MessageFormat messageFormat, BiFunction<String, Boolean, String> translator, TextChannel textChannel) {
        MessageCreateData discordMessage = DiscordSRV.translateMessage(messageFormat, translator);
        if (discordMessage == null) return;

        String webhookName = translator.apply(messageFormat.getWebhookName(), false);
        String webhookAvatarUrl = translator.apply(messageFormat.getWebhookAvatarUrl(), false);

        if (messageFormat.isUseWebhooks()) {
            WebhookUtil.deliverMessage(textChannel, webhookName, webhookAvatarUrl,
                    discordMessage.getContent(), discordMessage.getEmbeds().stream().findFirst().orElse(null));
        } else {
            DiscordUtil.queueMessage(textChannel, discordMessage, true);
        }
    }
}
