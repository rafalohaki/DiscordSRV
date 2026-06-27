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

package github.scarsz.discordsrv.util;

import github.scarsz.discordsrv.Debug;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.objects.WebhookMessage;
import net.dv8tion.jda.api.utils.messages.MessageRequest;
import net.dv8tion.jda.api.Permission;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;

import okio.Okio;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Webhook;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttp;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WebhookUtil {

    private static final Predicate<Webhook> LEGACY = hook -> hook.getName().endsWith("#1") || hook.getName().endsWith("#2");
    private static boolean loggedBannedWords = false;

    /**
     * Wraps an {@link InputStream} as a streaming OkHttp {@link RequestBody} so file uploads don't
     * have to buffer the whole payload in heap. Content length is {@code -1} (chunked transfer);
     * the underlying stream is closed exactly once after the body is written.
     */
    private static RequestBody streamingRequestBody(InputStream data) {
        return new RequestBody() {
            @Override
            public okhttp3.MediaType contentType() {
                return null;
            }

            @Override
            public long contentLength() {
                return -1L;
            }

            @Override
            public void writeTo(okio.BufferedSink sink) throws IOException {
                try (okio.Source source = okio.Okio.source(data)) {
                    sink.writeAll(source);
                }
            }
        };
    }

    static {
        try {
            // get rid of all previous webhooks created by DiscordSRV if they don't match a good channel
            for (Guild guild : DiscordSRV.getPlugin().getJda().getGuilds()) {
                Member selfMember = guild.getSelfMember();
                if (!selfMember.hasPermission(Permission.MANAGE_WEBHOOKS)) {
                    DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Unable to manage webhooks guild-wide in " + guild);
                    continue;
                }

                guild.retrieveWebhooks().queue(webhooks -> {
                    for (Webhook webhook : webhooks) {
                        Member owner = webhook.getOwner();
                        if (owner == null || !owner.getId().equals(selfMember.getId()) || !webhook.getName().startsWith("DiscordSRV")) {
                            continue;
                        }

                        if (DiscordSRV.getPlugin().getDestinationGameChannelNameForTextChannel(webhook.getChannel().asTextChannel()) == null) {
                            webhook.delete().reason("DiscordSRV: Purging webhook for unlinked channel").queue();
                        } else if (LEGACY.test(webhook)) {
                            webhook.delete().reason("DiscordSRV: Purging legacy formatted webhook").queue();
                        }
                    }
                });
            }
        } catch (Exception e) {
            DiscordSRV.warning("Failed to purge already existing webhooks: " + e.getMessage());
            DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, e);
        }
    }

    public static void deliverMessage(TextChannel channel, Player player, String message) {
        deliverMessage(channel, player, message, (Collection<? extends MessageEmbed>) null);
    }

    @SuppressWarnings("deprecation")
    public static void deliverMessage(TextChannel channel, Player player, String message, MessageEmbed embed) {
        deliverMessage(channel, player, player.getDisplayName(), message, embed);
    }

    @SuppressWarnings("deprecation")
    public static void deliverMessage(TextChannel channel, Player player, String message, Collection<? extends MessageEmbed> embeds) {
        deliverMessage(channel, player, player.getDisplayName(), message, embeds);
    }

    @SuppressWarnings("deprecation")
    public static void deliverMessage(TextChannel channel, Player player, String message, MessageEmbed embed, Map<String, InputStream> attachments, Collection<? extends ActionRow> interactions) {
        deliverMessage(channel, player, player.getDisplayName(), message, embed, attachments, interactions);
    }

    @SuppressWarnings("deprecation")
    public static void deliverMessage(TextChannel channel, Player player, String message, Collection<? extends MessageEmbed> embeds, Map<String, InputStream> attachments, Collection<? extends ActionRow> interactions) {
        deliverMessage(channel, player, player.getDisplayName(), message, embeds, attachments, interactions);
    }

    public static void deliverMessage(TextChannel channel, OfflinePlayer player, String displayName, String message, MessageEmbed embed) {
        deliverMessage(channel, player, displayName, message, embed, null, null);
    }

    public static void deliverMessage(TextChannel channel, OfflinePlayer player, String displayName, String message, Collection<? extends MessageEmbed> embeds) {
        deliverMessage(channel, player, displayName, message, embeds, null, null);
    }

    public static void deliverMessage(TextChannel channel, OfflinePlayer player, String displayName, String message, MessageEmbed embed, Map<String, InputStream> attachments, Collection<? extends ActionRow> interactions) {
        deliverMessage(channel, player, displayName, message, Collections.singletonList(embed), null, null);
    }

    public static void deliverMessage(TextChannel channel, OfflinePlayer player, String displayName, String message, Collection<? extends MessageEmbed> embeds, Map<String, InputStream> attachments, Collection<? extends ActionRow> interactions) {
        SchedulerUtil.runTaskAsynchronously(DiscordSRV.getPlugin(), () -> {
            String avatarUrl;
            if (player instanceof Player p) {
                avatarUrl = DiscordSRV.getAvatarUrl(p);
            } else {
                avatarUrl = DiscordSRV.getAvatarUrl(player.getName(), player.getUniqueId());
            }

            String username = DiscordSRV.config().getString("Experiment_WebhookChatMessageUsernameFormat")
                    .replace("%displayname%", displayName)
                    .replace("%username%", String.valueOf(player.getName()));
            String chatMessage = DiscordSRV.config().getString("Experiment_WebhookChatMessageFormat")
                    .replace("%displayname%", displayName)
                    .replace("%username%", player.getName())
                    .replace("%message%", message.replace("[", "\\["));
            chatMessage = PlaceholderUtil.replacePlaceholdersToDiscord(chatMessage, player);
            chatMessage = DiscordUtil.translateEmotes(chatMessage, channel.getGuild());
            username = PlaceholderUtil.replacePlaceholdersToDiscord(username, player);
            username = MessageUtil.strip(username);

            for (Map.Entry<Pattern, String> entry : DiscordSRV.getPlugin().getGameRegexes().entrySet()) {
                username = entry.getKey().matcher(username).replaceAll(Matcher.quoteReplacement(entry.getValue()));
                chatMessage = entry.getKey().matcher(chatMessage).replaceAll(Matcher.quoteReplacement(entry.getValue()));

                if (StringUtils.isBlank(username)) {
                    DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Not processing Minecraft message because the webhook username was cleared by a filter: " + entry.getKey().pattern());
                    return;
                }

                if (StringUtils.isBlank(chatMessage)) {
                    DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Not processing Minecraft message because the webhook content was cleared by a filter: " + entry.getKey().pattern());
                    return;
                }
            }

            String userId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(player.getUniqueId());
            if (userId != null) {
                Member member = DiscordUtil.getMemberById(userId);
                username = username
                        .replace("%discordname%", member != null ? member.getEffectiveName() : "")
                        .replace("%discordusername%", member != null ? member.getUser().getName() : "");
                if (member != null) {
                    if (DiscordSRV.config().getBoolean("Experiment_WebhookChatMessageAvatarFromDiscord"))
                        avatarUrl = member.getUser().getEffectiveAvatarUrl();
                    if (DiscordSRV.config().getBoolean("Experiment_WebhookChatMessageUsernameFromDiscord"))
                        username = member.getEffectiveName();
                }
            } else {
                username = username
                        .replace("%discordname%", "")
                        .replace("%discordusername%", "");
            }

            if (username.length() > 80) {
                DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "The webhook username in " + player.getName() + "'s message was too long! Reducing to 80 characters");
                username = username.substring(0, 80);
            }

            deliverMessage(channel, username, avatarUrl, chatMessage, embeds, attachments, interactions);
        });
    }

    public static void deliverMessage(TextChannel channel, String webhookName, String webhookAvatarUrl, String message, MessageEmbed embed) {
        executeWebhook(channel, webhookName, webhookAvatarUrl, null, message, Collections.singletonList(embed), null, null, true, true);
    }

    public static void deliverMessage(TextChannel channel, String webhookName, String webhookAvatarUrl, String message, MessageEmbed embed, boolean scheduleAsync) {
        executeWebhook(channel, webhookName, webhookAvatarUrl, null, message, Collections.singletonList(embed), null, null, true, scheduleAsync);
    }

    public static void deliverMessage(TextChannel channel, String webhookName, String webhookAvatarUrl, String message, Collection<? extends MessageEmbed> embeds) {
        executeWebhook(channel, webhookName, webhookAvatarUrl, null, message, embeds, null, null, true, true);
    }

    public static void deliverMessage(TextChannel channel, String webhookName, String webhookAvatarUrl, String message, Collection<? extends MessageEmbed> embeds, boolean scheduleAsync) {
        executeWebhook(channel, webhookName, webhookAvatarUrl, null, message, embeds, null, null, true, scheduleAsync);
    }

    public static void deliverMessage(TextChannel channel, String webhookName, String webhookAvatarUrl, String message, MessageEmbed embed, Map<String, InputStream> attachments, Collection<? extends ActionRow> interactions) {
        executeWebhook(channel, webhookName, webhookAvatarUrl, null, message, Collections.singletonList(embed), attachments, interactions, true, true);
    }

    public static void deliverMessage(TextChannel channel, String webhookName, String webhookAvatarUrl, String message, MessageEmbed embed, Map<String, InputStream> attachments, Collection<? extends ActionRow> interactions, boolean scheduleAsync) {
        executeWebhook(channel, webhookName, webhookAvatarUrl, null, message, Collections.singletonList(embed), attachments, interactions, true, scheduleAsync);
    }

    public static void deliverMessage(TextChannel channel, String webhookName, String webhookAvatarUrl, String message, Collection<? extends MessageEmbed> embeds, Map<String, InputStream> attachments, Collection<? extends ActionRow> interactions) {
        executeWebhook(channel, webhookName, webhookAvatarUrl, null, message, embeds, attachments, interactions, true, true);
    }

    public static void deliverMessage(TextChannel channel, String webhookName, String webhookAvatarUrl, String message, Collection<? extends MessageEmbed> embeds, Map<String, InputStream> attachments, Collection<? extends ActionRow> interactions, boolean scheduleAsync) {
        executeWebhook(channel, webhookName, webhookAvatarUrl, null, message, embeds, attachments, interactions, true, scheduleAsync);
    }

    public static void editMessage(TextChannel channel, String editMessageId, String message, MessageEmbed embed) {
        executeWebhook(channel, null, null, editMessageId, message, Collections.singletonList(embed), null, null, true, true);
    }

    public static void editMessage(TextChannel channel, String editMessageId, String message, MessageEmbed embed, boolean scheduleAsync) {
        executeWebhook(channel, null, null, editMessageId, message, Collections.singletonList(embed), null, null, true, scheduleAsync);
    }

    public static void editMessage(TextChannel channel, String editMessageId, String message, Collection<? extends MessageEmbed> embeds) {
        executeWebhook(channel, null, null, editMessageId, message, embeds, null, null, true, true);
    }

    public static void editMessage(TextChannel channel, String editMessageId, String message, Collection<? extends MessageEmbed> embeds, boolean scheduleAsync) {
        executeWebhook(channel, null, null, editMessageId, message, embeds, null, null, true, scheduleAsync);
    }

    public static void editMessage(TextChannel channel, String editMessageId, String message, MessageEmbed embed, Map<String, InputStream> attachments, Collection<? extends ActionRow> interactions) {
        executeWebhook(channel, null, null, editMessageId, message, Collections.singletonList(embed), attachments, interactions, true, true);
    }

    public static void editMessage(TextChannel channel, String editMessageId, String message, MessageEmbed embed, Map<String, InputStream> attachments, Collection<? extends ActionRow> interactions, boolean scheduleAsync) {
        executeWebhook(channel, null, null, editMessageId, message, Collections.singletonList(embed), attachments, interactions, true, scheduleAsync);
    }

    public static void editMessage(TextChannel channel, String editMessageId, String message, Collection<? extends MessageEmbed> embeds, Map<String, InputStream> attachments, Collection<? extends ActionRow> interactions) {
        executeWebhook(channel, null, null, editMessageId, message, embeds, attachments, interactions, true, true);
    }

    public static void editMessage(TextChannel channel, String editMessageId, String message, Collection<? extends MessageEmbed> embeds, Map<String, InputStream> attachments, Collection<? extends ActionRow> interactions, boolean scheduleAsync) {
        executeWebhook(channel, null, null, editMessageId, message, embeds, attachments, interactions, true, scheduleAsync);
    }

    // ─── Modern WebhookMessage-based API ───────────────────────────────────

    /**
     * Delivers a webhook message with pre-resolved name/avatar/content.
     * Use {@link WebhookMessage#builder(TextChannel)} to construct.
     */
    public static void deliver(WebhookMessage msg) {
        executeWebhook(
                msg.channel(),
                msg.webhookName(),
                msg.webhookAvatarUrl(),
                msg.editMessageId(),
                msg.message(),
                msg.embeds(),
                msg.attachments(),
                msg.interactions(),
                true,
                msg.scheduleAsync()
        );
    }

    /**
     * Delivers a webhook message from a Minecraft player — resolves avatar URL,
     * applies username/message format placeholders, runs regex filters, and
     * handles account-link Discord name/avatar override.
     *
     * <p>The {@link WebhookMessage#webhookName()} and {@link WebhookMessage#message()}
     * are treated as raw; this method applies the configured format strings.
     * Use {@link #deliver(WebhookMessage)} for pre-formatted messages.
     *
     * @param player      the offline player (may be online)
     * @param displayName display name for placeholder substitution
     * @param rawMessage  raw message content (before format placeholder substitution)
     */
    public static void deliverFromPlayer(
            org.bukkit.OfflinePlayer player,
            String displayName,
            String rawMessage,
            WebhookMessage msg
    ) {
        SchedulerUtil.runTaskAsynchronously(DiscordSRV.getPlugin(), () -> {
            String avatarUrl;
            if (player instanceof Player p) {
                avatarUrl = DiscordSRV.getAvatarUrl(p);
            } else {
                avatarUrl = DiscordSRV.getAvatarUrl(player.getName(), player.getUniqueId());
            }

            String username = DiscordSRV.config().getString("Experiment_WebhookChatMessageUsernameFormat")
                    .replace("%displayname%", displayName)
                    .replace("%username%", String.valueOf(player.getName()));
            String chatMessage = DiscordSRV.config().getString("Experiment_WebhookChatMessageFormat")
                    .replace("%displayname%", displayName)
                    .replace("%username%", player.getName())
                    .replace("%message%", rawMessage.replace("[", "\\["));
            chatMessage = PlaceholderUtil.replacePlaceholdersToDiscord(chatMessage, player);
            chatMessage = DiscordUtil.translateEmotes(chatMessage, msg.channel().getGuild());
            username = PlaceholderUtil.replacePlaceholdersToDiscord(username, player);
            username = MessageUtil.strip(username);

            for (Map.Entry<Pattern, String> entry : DiscordSRV.getPlugin().getGameRegexes().entrySet()) {
                username = entry.getKey().matcher(username).replaceAll(Matcher.quoteReplacement(entry.getValue()));
                chatMessage = entry.getKey().matcher(chatMessage).replaceAll(Matcher.quoteReplacement(entry.getValue()));

                if (StringUtils.isBlank(username)) {
                    DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Not processing Minecraft message because the webhook username was cleared by a filter: " + entry.getKey().pattern());
                    return;
                }

                if (StringUtils.isBlank(chatMessage)) {
                    DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Not processing Minecraft message because the webhook content was cleared by a filter: " + entry.getKey().pattern());
                    return;
                }
            }

            String userId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(player.getUniqueId());
            if (userId != null) {
                Member member = DiscordUtil.getMemberById(userId);
                username = username
                        .replace("%discordname%", member != null ? member.getEffectiveName() : "")
                        .replace("%discordusername%", member != null ? member.getUser().getName() : "");
                if (member != null) {
                    if (DiscordSRV.config().getBoolean("Experiment_WebhookChatMessageAvatarFromDiscord"))
                        avatarUrl = member.getUser().getEffectiveAvatarUrl();
                    if (DiscordSRV.config().getBoolean("Experiment_WebhookChatMessageUsernameFromDiscord"))
                        username = member.getEffectiveName();
                }
            } else {
                username = username
                        .replace("%discordname%", "")
                        .replace("%discordusername%", "");
            }

            if (username.length() > 80) {
                DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "The webhook username in " + player.getName() + "'s message was too long! Reducing to 80 characters");
                username = username.substring(0, 80);
            }

            // Build the final message with resolved name/avatar/content
            WebhookMessage resolved = WebhookMessage.builder(msg.channel())
                    .webhookName(username)
                    .webhookAvatarUrl(avatarUrl)
                    .message(chatMessage)
                    .embeds(msg.embeds())
                    .attachments(msg.attachments())
                    .interactions(msg.interactions())
                    .scheduleAsync(false) // already on async scheduler
                    .build();
            deliver(resolved);
        });
    }

    private static void executeWebhook(TextChannel channel, String webhookName, String webhookAvatarUrl, String editMessageId, String message, Collection<? extends MessageEmbed> embeds, Map<String, InputStream> attachments, Collection<? extends ActionRow> interactions, boolean allowSecondAttempt, boolean scheduleAsync) {
        if (channel == null) {
            if (attachments != null) {
                attachments.values().forEach(inputStream -> {
                    try {
                        inputStream.close();
                    } catch (IOException ignore) {
                    }
                });
            }
            return;
        }

        String webhookUrlForChannel = getWebhookUrlToUseForChannel(channel);
        if (webhookUrlForChannel == null) {
            if (attachments != null) {
                attachments.values().forEach(inputStream -> {
                    try {
                        inputStream.close();
                    } catch (IOException ignore) {
                    }
                });
            }
            return;
        }

        if (editMessageId != null) {
            webhookUrlForChannel += "/messages/" + editMessageId;
        }
        String webhookUrl = webhookUrlForChannel;

        Runnable task = () -> {
            try {
                JSONObject jsonObject = new JSONObject();
                if (editMessageId == null) {
                    String webName = webhookName;
                    for (Map.Entry<Pattern, String> entry : DiscordSRV.getPlugin().getWebhookUsernameRegexes().entrySet()) {
                        webName = entry.getKey().matcher(webName).replaceAll(Matcher.quoteReplacement(entry.getValue()));
                    }

                    // Handle Discord banned words in a way that isn't against their developer policy
                    String username = webName;
                    username = username
                            .replaceAll("(?i)(cly)d(e)", "$1*$2")
                            .replaceAll("(?i)(d)i(scord)", "$1*$2");
                    if (!username.equals(webName) && loggedBannedWords) {
                        DiscordSRV.info("Some webhook usernames are being altered to remove blocked words (eg. Clyde and Discord)");
                        loggedBannedWords = true;
                    }

                    jsonObject.put("username", username);
                    jsonObject.put("avatar_url", webhookAvatarUrl);
                }

                if (StringUtils.isNotBlank(message)) jsonObject.put("content", message);
                if (embeds != null) {
                    JSONArray jsonArray = new JSONArray();
                    for (MessageEmbed embed : embeds) {
                        if (embed != null) {
                            jsonArray.put(embed.toData().toMap());
                        }
                    }
                    jsonObject.put("embeds", jsonArray);
                }
                if (interactions != null) {
                    JSONArray jsonArray = new JSONArray();
                    // JDA 6 components V2: SerializableData was removed from Component; use ComponentSerializer
                    // (added in JDA 6.0 PR #2906) which produces DataObject. toMap() then feeds org.json.JSONObject.
                    net.dv8tion.jda.api.components.utils.ComponentSerializer serializer =
                            new net.dv8tion.jda.api.components.utils.ComponentSerializer();
                    for (ActionRow actionRow : interactions) {
                        jsonArray.put(new JSONObject(serializer.serialize(actionRow).toMap()));
                    }
                    jsonObject.put("components", jsonArray);
                }
                List<String> attachmentIndex = null;
                if (attachments != null) {
                    attachmentIndex = new ArrayList<>(attachments.size());
                    JSONArray jsonArray = new JSONArray();
                    int i = 0;
                    for (String name : attachments.keySet()) {
                        attachmentIndex.add(name);
                        JSONObject attachmentObject = new JSONObject();
                        attachmentObject.put("id", i);
                        attachmentObject.put("filename", name);
                        jsonArray.put(attachmentObject);
                        i++;
                    }
                    jsonObject.put("attachments", jsonArray);
                }

                JSONObject allowedMentions = new JSONObject();
                Set<String> parse = MessageRequest.getDefaultMentions().stream()
                        .filter(Objects::nonNull)
                        .map(Message.MentionType::getParseKey)
                        .collect(Collectors.toSet());
                allowedMentions.put("parse", parse);
                jsonObject.put("allowed_mentions", allowedMentions);

                DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Sending webhook payload: " + jsonObject);

                MultipartBody.Builder bodyBuilder = new MultipartBody.Builder().setType(MultipartBody.FORM);
                bodyBuilder.addFormDataPart("payload_json", null, RequestBody.create(MediaType.get("application/json"), jsonObject.toString()));

                if (attachmentIndex != null) {
                    for (int i = 0; i < attachmentIndex.size(); i++) {
                        String name = attachmentIndex.get(i);
                        InputStream data = attachments.get(name);
                        if (data != null) {
                            bodyBuilder.addFormDataPart("files[" + i + "]", name, streamingRequestBody(data));
                        }
                    }
                }

                Request.Builder requestBuilder = new Request.Builder().url(webhookUrl)
                        .header("User-Agent", "DiscordSRV/" + DiscordSRV.getPlugin().getDescription().getVersion());
                if (editMessageId == null) {
                    requestBuilder.post(bodyBuilder.build());
                } else {
                    requestBuilder.patch(bodyBuilder.build());
                }

                OkHttpClient httpClient = DiscordSRV.getPlugin().getJda().getHttpClient();
                try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
                    int status = response.code();
                    if (status == 404) {
                        // 404 = Invalid Webhook (most likely to have been deleted)
                        DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Webhook delivery returned 404, marking webhooks URLs as invalid to let them regenerate" + (allowSecondAttempt ? " & trying again" : ""));
                        invalidWebhookUrlForChannel(channel); // tell it to get rid of the urls & get new ones
                        if (allowSecondAttempt)
                            executeWebhook(channel, webhookName, webhookAvatarUrl, editMessageId, message, embeds, attachments, interactions, false, scheduleAsync);
                        return;
                    }
                    String body = response.body().string();
                    try {
                        JSONObject jsonObj = new JSONObject(body);
                        if (jsonObj.has("code")) {
                            // 10015 = unknown webhook, https://discord.com/developers/docs/topics/opcodes-and-status-codes#json-json-error-codes
                            if (jsonObj.getInt("code") == 10015) {
                                DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Webhook delivery returned 10015 (Unknown Webhook), marking webhooks url's as invalid to let them regenerate" + (allowSecondAttempt ? " & trying again" : ""));
                                invalidWebhookUrlForChannel(channel); // tell it to get rid of the urls & get new ones
                                if (allowSecondAttempt)
                                    executeWebhook(channel, webhookName, webhookAvatarUrl, editMessageId, message, embeds, attachments, interactions, false, scheduleAsync);
                                return;
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                    if (editMessageId == null ? status == 204 : status == 200) {
                        DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Received API response for webhook message delivery: " + status);
                    } else {
                        DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Received unexpected API response for webhook message delivery: " + status + " for request: " + jsonObject.toString() + ", response: " + body);
                    }
                }
            } catch (Exception e) {
                DiscordSRV.error("Failed to deliver webhook message to Discord: " + e.getMessage());
                DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, e);
                if (attachments != null) {
                    attachments.values().forEach(inputStream -> {
                        try {
                            inputStream.close();
                        } catch (IOException ignore) {
                        }
                    });
                }
            }
        };

        if (scheduleAsync) {
            SchedulerUtil.runTaskAsynchronously(DiscordSRV.getPlugin(), task);
        } else {
            task.run();
        }
    }

    private static final Map<String, String> channelWebhookUrls = new ConcurrentHashMap<>();

    public static void invalidWebhookUrlForChannel(TextChannel textChannel) {
        String channelId = textChannel.getId();
        channelWebhookUrls.remove(channelId);
    }

    public static String getWebhookUrlToUseForChannel(TextChannel channel) {
        final String channelId = channel.getId();
        return channelWebhookUrls.computeIfAbsent(channelId, cid -> {
            List<Webhook> hooks = new ArrayList<>();
            final Guild guild = channel.getGuild();
            final Member selfMember = guild.getSelfMember();

            String bannedWebhookFormat = "DiscordSRV " + cid; // This format is blocked by Discord
            String webhookFormat = "DSRV " + cid;

            // Check if we have permission guild-wide
            List<Webhook> result;
            if (guild.getSelfMember().hasPermission(Permission.MANAGE_WEBHOOKS)) {
                result = guild.retrieveWebhooks().complete();
            } else {
                result = channel.retrieveWebhooks().complete();
            }

            result.stream()
                    .filter(webhook -> webhook.getName().startsWith(webhookFormat) || webhook.getName().startsWith(bannedWebhookFormat))
                    .filter(webhook -> {
                        // Filter to what we can modify
                        Member owner = webhook.getOwner();
                        return owner != null && selfMember.getId().equals(owner.getId());
                    })
                    .filter(webhook -> {
                        if (!webhook.getChannel().asTextChannel().equals(channel)) {
                            webhook.delete().reason("DiscordSRV: Purging lost webhook").queue();
                            return false;
                        }
                        return true;
                    })
                    .filter(webhook -> {
                        if (LEGACY.test(webhook)) {
                            webhook.delete().reason("DiscordSRV: Purging legacy formatted webhook").queue();
                            return false;
                        }
                        return true;
                    })
                    .forEach(hooks::add);

            if (hooks.isEmpty()) {
                hooks.add(createWebhook(channel, webhookFormat));
            } else if (hooks.size() > 1) {
                for (int index = 1; index < hooks.size(); index++) {
                    hooks.get(index).delete().reason("DiscordSRV: Purging duplicate webhook").queue();
                }
            }

            return hooks.stream().map(Webhook::getUrl).findAny().orElse(null);
        });
    }

    public static Webhook createWebhook(TextChannel channel, String name) {
        try {
            Webhook webhook = channel.createWebhook(name).reason("DiscordSRV: Creating webhook").complete();
            DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Created webhook " + webhook.getName() + " to deliver messages to text channel #" + channel.getName());
            return webhook;
        } catch (Exception e) {
            DiscordSRV.error("Failed to create webhook " + name + " for message delivery: " + e.getMessage());
            return null;
        }
    }

    public static String getWebhookUrlFromCache(TextChannel channel) {
        return channelWebhookUrls.get(channel.getId());
    }

}
