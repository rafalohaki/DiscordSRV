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

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable webhook delivery request — replaces 24+ overloaded
 * {@code WebhookUtil.deliverMessage} / {@code editMessage} methods.
 *
 * <p>Java 25 record with a fluent builder. Use:
 * <pre>{@code
 * WebhookMessage msg = WebhookMessage.builder(channel)
 *         .player(player)
 *         .message("hello")
 *         .embed(embed)
 *         .build();
 * WebhookUtil.deliver(msg);
 * }</pre>
 *
 * <p>For edit operations (no webhook name/avatar), use {@link #editBuilder(TextChannel, String)}.
 */
public record WebhookMessage(
        TextChannel channel,
        @Nullable String webhookName,
        @Nullable String webhookAvatarUrl,
        @Nullable String editMessageId,
        @Nullable String message,
        @Nullable Collection<? extends MessageEmbed> embeds,
        @Nullable Map<String, InputStream> attachments,
        @Nullable Collection<? extends ActionRow> interactions,
        boolean scheduleAsync
) {

    /** Canonical single-embed convenience. */
    public WebhookMessage {
        if (embeds == null && message == null) {
            // allow — both null means "nothing to send", caller should check
        }
        // Defensive copy: wrap single embed into list if needed (handled by builder)
    }

    /** True if this is an edit (has editMessageId), false if a new webhook post. */
    public boolean isEdit() {
        return editMessageId != null;
    }

    /** Embeds as a non-null list (empty if none). */
    public Collection<? extends MessageEmbed> embedsOrEmpty() {
        return embeds != null ? embeds : Collections.emptyList();
    }

    /**
     * Builder for a new webhook message delivery.
     */
    public static Builder builder(TextChannel channel) {
        return new Builder(channel, null);
    }

    /**
     * Builder for editing an existing webhook message.
     *
     * @param channel       the channel containing the message
     * @param editMessageId the message ID to edit
     */
    public static Builder editBuilder(TextChannel channel, String editMessageId) {
        return new Builder(channel, editMessageId);
    }

    /** Fluent builder. */
    public static final class Builder {
        private final TextChannel channel;
        private final String editMessageId;
        private String webhookName;
        private String webhookAvatarUrl;
        private String message;
        private Collection<? extends MessageEmbed> embeds;
        private Map<String, InputStream> attachments;
        private Collection<? extends ActionRow> interactions;
        private boolean scheduleAsync = true;

        Builder(TextChannel channel, String editMessageId) {
            this.channel = channel;
            this.editMessageId = editMessageId;
        }

        /** Sets webhook display name (ignored for edits). */
        public Builder webhookName(String name) { this.webhookName = name; return this; }

        /** Sets webhook avatar URL (ignored for edits). */
        public Builder webhookAvatarUrl(String url) { this.webhookAvatarUrl = url; return this; }

        /** Sets message content. */
        public Builder message(String msg) { this.message = msg; return this; }

        /** Sets a single embed. */
        public Builder embed(MessageEmbed embed) {
            this.embeds = embed != null ? List.of(embed) : null;
            return this;
        }

        /** Sets multiple embeds. */
        public Builder embeds(Collection<? extends MessageEmbed> embeds) {
            this.embeds = embeds;
            return this;
        }

        /** Sets file attachments (name → stream). */
        public Builder attachments(Map<String, InputStream> attachments) {
            this.attachments = attachments;
            return this;
        }

        /** Sets interaction components (action rows). */
        public Builder interactions(Collection<? extends ActionRow> interactions) {
            this.interactions = interactions;
            return this;
        }

        /** Whether to schedule delivery on the async scheduler (default true). */
        public Builder scheduleAsync(boolean async) { this.scheduleAsync = async; return this; }

        /**
         * Convenience: sets webhook name and avatar from a player's display name
         * and avatar URL. For use with {@link WebhookUtil#deliverFromPlayer}.
         */
        public Builder player(Player player) {
            this.webhookName = player.getDisplayName();
            this.webhookAvatarUrl = github.scarsz.discordsrv.DiscordSRV.getAvatarUrl(player);
            return this;
        }

        public WebhookMessage build() {
            return new WebhookMessage(
                    channel, webhookName, webhookAvatarUrl, editMessageId,
                    message, embeds, attachments, interactions, scheduleAsync
            );
        }
    }
}
