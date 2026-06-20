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

import github.scarsz.discordsrv.Debug;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.hooks.PluginHook;
import github.scarsz.discordsrv.util.LangUtil;
import github.scarsz.discordsrv.util.MessageUtil;
import github.scarsz.discordsrv.util.PlayerUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * Bridge between DiscordSRV and a third-party Minecraft chat plugin.
 *
 * <p>Implementations resolve a DiscordSRV channel name to a
 * {@link ChannelInfo} record (channel name, nickname, color, recipients).
 * The default {@link #broadcastMessageToChannel(String, Component)} handles
 * the common formatting + delivery pipeline, eliminating ~60 lines of
 * duplicated code across 10 chat hooks.
 *
 * <p>Java 25 sealed interface — the compiler enforces that only known chat
 * hooks can implement this interface, preventing accidental implementations.
 * New chat plugins must be added to the {@code permits} list.
 */
public interface ChatHook extends PluginHook {

    /**
     * Resolves a DiscordSRV channel name to a {@link ChannelInfo}.
     *
     * <p>Implementations should return {@code null} if the channel does not
     * exist in the hooked chat plugin.
     *
     * @param channelName the DiscordSRV channel name (case-sensitive)
     * @return channel info, or {@code null} if no matching channel
     */
    @Nullable
    ChannelInfo resolveChannel(String channelName);

    /**
     * Delivers a pre-formatted message to the given recipients.
     *
     * <p>Default implementation sends via {@link MessageUtil#sendMessage}.
     * Override for chat plugins that have their own send API (e.g. FancyChat,
     * Herochat's {@code sendRawMessage}).
     *
     * @param recipients players who should receive the message
     * @param formattedMessage the legacy-translated message string
     */
    default void deliverToRecipients(java.util.Collection<? extends Player> recipients, String formattedMessage) {
        Plugin plugin = DiscordSRV.getPlugin();
        Component component = MessageUtil.toComponent(formattedMessage);
        for (Player recipient : recipients) {
            recipient.getScheduler().run(plugin, task -> {
                try {
                    recipient.sendMessage(component);
                } catch (Throwable t) {
                    DiscordSRV.debug(Debug.DISCORD_TO_MINECRAFT,
                            "Failed to send Discord message to " + recipient.getName() + ": " + t.getMessage());
                }
            }, null);
        }
    }

    /**
     * Default broadcast pipeline — formats the message with channel
     * placeholders, translates legacy codes, delivers to recipients, and
     * notifies players of mentions.
     *
     * <p>Override only if the chat plugin requires custom delivery logic
     * that cannot be expressed via {@link #resolveChannel} +
     * {@link #deliverToRecipients}.
     */
    default void broadcastMessageToChannel(String channel, Component message) {
        ChannelInfo info = resolveChannel(channel);
        if (info == null) {
            DiscordSRV.debug(Debug.DISCORD_TO_MINECRAFT,
                    "Attempted to broadcast message to channel \"" + channel
                            + "\" but the channel doesn't exist; aborting message send");
            return;
        }

        String legacy = MessageUtil.toLegacy(message);
        String plainMessage = LangUtil.Message.CHAT_CHANNEL_MESSAGE.toString()
                .replace("%channelcolor%", info.colorOrEmpty())
                .replace("%channelname%", info.name())
                .replace("%channelnickname%", info.nicknameOrName())
                .replace("%message%", legacy);

        String translatedMessage = MessageUtil.translateLegacy(plainMessage);

        if (info.recipients().isEmpty()) {
            DiscordSRV.debug(Debug.DISCORD_TO_MINECRAFT,
                    "Chat channel \"" + info.name() + "\" has 0 recipients — message will not be visible to any player");
        }

        deliverToRecipients(info.recipients(), translatedMessage);
        PlayerUtil.notifyPlayersOfMentions(info.recipients()::contains, legacy);
    }

    /** Legacy String-based broadcast — delegates to the Component variant. */
    @Deprecated
    default void broadcastMessageToChannel(String channel, String message) {
        broadcastMessageToChannel(channel, MessageUtil.toComponent(message));
    }
}
