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
import github.scarsz.discordsrv.util.MessageUtil;
import github.scarsz.discordsrv.util.PlaceholderUtil;
import github.scarsz.discordsrv.util.TimeUtil;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * Reusable placeholder translator for Minecraft → Discord message formatting.
 *
 * <p>Extracted from 5 duplicated BiFunction lambdas across listeners
 * (PlayerDeathListener, PlayerAdvancementDoneListener, PlayerAchievementsListener,
 * JoinLeaveMessageSender, AlertListener). Each listener had an ~18-line lambda
 * with identical base placeholders; only the event-specific placeholders differed.
 *
 * <p>Usage:
 * <pre>{@code
 * var translator = new MessageTranslator(player, displayName, avatarUrl, botAvatarUrl, botName, destinationChannel)
 *         .withPlaceholder("deathmessage", finalDeathMessage)
 *         .withPlaceholder("deathmessagenoescapes", MessageUtil.strip(finalDeathMessage));
 * BiFunction<String, Boolean, String> fn = translator.toFunction();
 * }</pre>
 *
 * <p>Java 25 record — immutable, thread-safe, allocation-cheap.
 */
public record MessageTranslator(
        Player player,
        String displayName,
        String avatarUrl,
        String botAvatarUrl,
        String botName,
        TextChannel destinationChannel,
        Map<String, String> customPlaceholders
) {

    /**
     * Base translator without event-specific placeholders.
     */
    public MessageTranslator(Player player, String displayName, String avatarUrl,
                             String botAvatarUrl, String botName, TextChannel destinationChannel) {
        this(player, displayName, avatarUrl, botAvatarUrl, botName, destinationChannel, Map.of());
    }

    /**
     * Returns a new translator with an additional event-specific placeholder.
     * The placeholder key is wrapped in {@code %...%} automatically.
     *
     * @param key   placeholder name without {@code %} delimiters (e.g. {@code "deathmessage"})
     * @param value raw value; will be escaped when {@code needsEscape=true} in the translator function
     * @return new translator with the placeholder added
     */
    public MessageTranslator withPlaceholder(String key, String value) {
        var newMap = new java.util.HashMap<>(customPlaceholders);
        newMap.put(key, value != null ? value : "");
        return new MessageTranslator(player, displayName, avatarUrl, botAvatarUrl, botName,
                destinationChannel, Map.copyOf(newMap));
    }

    /**
     * Builds the BiFunction consumed by {@link DiscordSRV#translateMessage}.
     *
     * <p>The function applies:
     * <ol>
     *   <li>Base placeholders: {@code %time%}, {@code %date%}, {@code %username%},
     *       {@code %displayname%}, {@code %usernamenoescapes%}, {@code %displaynamenoescapes%},
     *       {@code %world%}, {@code %embedavatarurl%}, {@code %botavatarurl%}, {@code %botname%}</li>
     *   <li>Custom placeholders (event-specific)</li>
     *   <li>Discord emote translation (if destination channel is set)</li>
     *   <li>PlaceholderAPI placeholders</li>
     * </ol>
     *
     * @return BiFunction that translates content strings; returns {@code null} for null input
     */
    public BiFunction<String, Boolean, String> toFunction() {
        return (content, needsEscape) -> {
            if (content == null) return null;

            content = content
                    .replaceAll("%time%|%date%", TimeUtil.timeStamp())
                    .replace("%username%", needsEscape ? DiscordUtil.escapeMarkdown(player.getName()) : player.getName())
                    .replace("%displayname%", needsEscape ? DiscordUtil.escapeMarkdown(displayName) : displayName)
                    .replace("%usernamenoescapes%", player.getName())
                    .replace("%displaynamenoescapes%", displayName)
                    .replace("%world%", player.getWorld().getName())
                    .replace("%embedavatarurl%", avatarUrl)
                    .replace("%botavatarurl%", botAvatarUrl)
                    .replace("%botname%", botName);

            // Apply event-specific placeholders
            for (var entry : customPlaceholders.entrySet()) {
                String key = "%" + entry.getKey() + "%";
                String value = entry.getValue();
                // Heuristic: placeholders ending in "noescapes" are never escaped;
                // all others are escaped when needsEscape is true.
                if (needsEscape && !entry.getKey().endsWith("noescapes")) {
                    value = MessageUtil.strip(DiscordUtil.escapeMarkdown(value));
                }
                content = content.replace(key, value);
            }

            if (destinationChannel != null) {
                content = DiscordUtil.translateEmotes(content, destinationChannel.getGuild());
            }
            content = PlaceholderUtil.replacePlaceholdersToDiscord(content, player);
            return content;
        };
    }

    /**
     * Convenience factory — resolves avatar URL, bot avatar URL, bot name, and display name
     * from the player and the plugin's current state. Most listeners can use this instead
     * of the full constructor.
     */
    public static MessageTranslator forPlayer(Player player, TextChannel destinationChannel) {
        String displayName = player.getDisplayName() != null && !player.getDisplayName().isBlank()
                ? MessageUtil.strip(player.getDisplayName()) : "";
        String avatarUrl = DiscordSRV.getAvatarUrl(player);
        String botAvatarUrl = DiscordUtil.getJda().getSelfUser().getEffectiveAvatarUrl();
        String botName = DiscordSRV.getPlugin().getMainGuild() != null
                ? DiscordSRV.getPlugin().getMainGuild().getSelfMember().getEffectiveName()
                : DiscordUtil.getJda().getSelfUser().getName();
        return new MessageTranslator(player, displayName, avatarUrl, botAvatarUrl, botName, destinationChannel);
    }
}
