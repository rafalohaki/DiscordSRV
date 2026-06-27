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
import github.scarsz.discordsrv.util.MessageUtil;
import github.scarsz.discordsrv.util.PluginUtil;
import github.scarsz.discordsrv.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * ChatHook for the WpmeCore Chat addon — handles Discord → Minecraft only.
 *
 * <p>When registered, DiscordSRV delegates Discord→MC message delivery to
 * {@link #broadcastMessageToChannel} (inherited from {@link ChatHook}),
 * which delivers the formatted component to each online player via
 * {@code player.getScheduler().run()} (Folia-safe). Recipients are resolved
 * from WpmeCore's {@code ChannelService} when the channel name maps to a
 * configured WpmeCore channel; otherwise all online players receive the
 * message.
 *
 * <p><b>MC → Discord is NOT handled here.</b> DiscordSRV shades Adventure
 * under {@code github.scarsz.discordsrv.dependencies.kyori.adventure.*},
 * which makes {@code AsyncChatEvent.message()} return an incompatible
 * {@code Component} type at runtime ({@code NoSuchMethodError}). MC→Discord
 * is handled by the Chat addon's {@code DiscordSrvBridge}, which runs in
 * the Chat plugin's classloader where Adventure is NOT relocated.
 *
 * <p>Because this hook is registered as a {@link ChatHook}, DiscordSRV
 * skips registering its own {@code ModernPlayerChatListener} (line 1045
 * of DiscordSRV.java). The {@code DiscordSrvBridge} fills that gap.
 *
 * <p>Soft-dep: uses the Bukkit {@code ServicesManager} to look up
 * {@code ChannelService} at runtime — no compile-time dependency on WpmeCore.
 */
public class WpmeCoreChatHook implements ChatHook {

    /** WpmeCore ChannelService class name (looked up via ServicesManager). */
    private static final String CHANNEL_SERVICE_CLASS = "org.rafalohaki.wpmecore.api.channel.ChannelService";

    // --- Cached reflection (resolved once on first resolveChannel call) ---
    private volatile @Nullable Class<?> channelServiceClass;
    private volatile @Nullable Method channelByIdMethod;
    private volatile @Nullable Method membersOfMethod;
    private volatile @Nullable Method isPresentMethod;
    private volatile @Nullable Method getMethod;
    private volatile @Nullable Method channelIdMethod;
    private volatile boolean reflectionInitialized = false;

    @Override
    public @Nullable ChannelInfo resolveChannel(String channelName) {
        // null or "global" → broadcast to all online players
        if (channelName == null || channelName.equalsIgnoreCase("global")) {
            return globalChannelInfo();
        }

        // Try WpmeCore ChannelService for channel-specific recipients
        Object channelService = lookupChannelService();
        if (channelService != null) {
            try {
                ensureReflectionInitialized(channelService);
                if (channelByIdMethod == null) {
                    // Reflection init failed — fall back to global
                    return globalChannelInfo();
                }
                Object channelOpt = channelByIdMethod.invoke(channelService, channelName);
                if (channelOpt != null && (boolean) isPresentMethod.invoke(channelOpt)) {
                    Object channel = getMethod.invoke(channelOpt);
                    @SuppressWarnings("unchecked")
                    List<UUID> memberUuids = (List<UUID>) membersOfMethod.invoke(channelService, channelName);
                    List<Player> recipients = new ArrayList<>();
                    for (UUID uuid : memberUuids) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            recipients.add(p);
                        }
                    }
                    String channelId = (String) channelIdMethod.invoke(channel);
                    return new ChannelInfo(channelId != null ? channelId : channelName, null, "", recipients);
                }
            } catch (Throwable t) {
                DiscordSRV.debug(Debug.DISCORD_TO_MINECRAFT,
                        "WpmeCoreChatHook: failed to resolve channel \"" + channelName + "\" via ChannelService: " + t.getMessage());
            }
        }

        // Unknown channel — fall back to all online players rather than dropping
        return globalChannelInfo();
    }

    /**
     * Initialize cached Method objects from the ChannelService instance.
     * Called once on first resolveChannel; subsequent calls are no-ops.
     * If the ChannelService class cannot be found or methods are missing,
     * the fields stay null and resolveChannel falls back to global.
     */
    private void ensureReflectionInitialized(@NotNull Object channelService) {
        if (reflectionInitialized) return;
        synchronized (this) {
            if (reflectionInitialized) return;
            try {
                Class<?> svcClass = channelService.getClass();
                channelByIdMethod = svcClass.getMethod("channelById", String.class);
                membersOfMethod = svcClass.getMethod("membersOf", String.class);
                channelIdMethod = Class.forName(
                        "org.rafalohaki.wpmecore.api.channel.Channel").getMethod("id");
                // Optional's methods are on the concrete Optional type
                Class<?> optionalClass = channelByIdMethod.getReturnType();
                isPresentMethod = optionalClass.getMethod("isPresent");
                getMethod = optionalClass.getMethod("get");
                channelServiceClass = svcClass;
            } catch (Throwable t) {
                DiscordSRV.debug(Debug.DISCORD_TO_MINECRAFT,
                        "WpmeCoreChatHook: reflection init failed: " + t.getMessage());
            }
            reflectionInitialized = true;
        }
    }

    private @NotNull ChannelInfo globalChannelInfo() {
        List<Player> recipients = new ArrayList<>(PlayerUtil.getOnlinePlayers(false));
        return new ChannelInfo("Global", null, "", recipients);
    }

    /**
     * Override broadcast pipeline to skip the {@code [channel]} prefix for
     * the global channel. The default {@link ChatHook#broadcastMessageToChannel}
     * formats every message as {@code %channelcolor%[%channelnickname%] %message%}
     * — for the global channel (the default DiscordSRV channel), the
     * {@code [Global]} prefix is redundant noise. For non-global channels
     * (e.g. staff, local), the prefix is useful and the default pipeline runs.
     */
    @Override
    public void broadcastMessageToChannel(String channel, net.kyori.adventure.text.Component message) {
        if (channel == null || channel.equalsIgnoreCase("global")) {
            // Global: deliver the message as-is, no [Global] prefix.
            String legacy = MessageUtil.toLegacy(message);
            String translated = MessageUtil.translateLegacy(legacy);
            DiscordSRV.debug(Debug.DISCORD_TO_MINECRAFT,
                    "WpmeCoreChatHook.broadcastMessageToChannel: global, " + translated);
            List<Player> recipients = new ArrayList<>(PlayerUtil.getOnlinePlayers(false));
            deliverToRecipients(recipients, translated);
            PlayerUtil.notifyPlayersOfMentions(recipients::contains, legacy);
            return;
        }
        // Non-global: use default pipeline (with [channel] prefix).
        ChatHook.super.broadcastMessageToChannel(channel, message);
    }

    /**
     * Override deliverToRecipients to bypass Adventure entirely.
     *
     * <p><b>Why:</b> DiscordSRV's Shadow plugin relocates {@code net.kyori} to
     * {@code github.scarsz.discordsrv.dependencies.kyori} (build.gradle.kts
     * line 115). This relocates ALL Adventure type references in DiscordSRV's
     * bytecode, including method signatures of external API classes. So
     * {@code Player.sendMessage(Component)} in the compiled bytecode becomes
     * {@code Player.sendMessage(relocatedComponent)}, but the actual server
     * method expects {@code net.kyori.adventure.text.Component} — causing
     * {@code NoSuchMethodError} at runtime.
     *
     * <p>{@link MessageUtil#sendMessage} goes through the {@code BukkitAudiences}
     * adapter, which can fail on Folia/Canvas. Instead, we call
     * {@code recipient.sendMessage(String)} directly — the {@code String}
     * parameter is a Java type, NOT relocated by Shadow, so the method
     * resolves correctly. The {@code formattedMessage} from
     * {@code broadcastMessageToChannel} is already legacy-translated
     * (section-sign color codes) by {@link MessageUtil#translateLegacy},
     * so colors render correctly via {@code CommandSender.sendMessage(String)}.
     *
     * <p>The default implementation in {@link ChatHook} catches the
     * {@code NoSuchMethodError} silently (try/catch Throwable → debug log),
     * so messages vanish without any visible error in the console.
     */
    @Override
    public void deliverToRecipients(@NotNull Collection<? extends Player> recipients,
                                    @NotNull String formattedMessage) {
        Plugin plugin = DiscordSRV.getPlugin();
        DiscordSRV.debug(Debug.DISCORD_TO_MINECRAFT,
                "WpmeCoreChatHook.deliverToRecipients: " + recipients.size() + " recipients, message=" + formattedMessage);
        for (Player recipient : recipients) {
            recipient.getScheduler().run(plugin, task -> {
                try {
                    // Bypass Adventure entirely — String parameter is NOT relocated.
                    // formattedMessage already has §-codes from MessageUtil.translateLegacy.
                    recipient.sendMessage(formattedMessage);
                } catch (Throwable t) {
                    DiscordSRV.debug(Debug.DISCORD_TO_MINECRAFT,
                            "Failed to send Discord message to " + recipient.getName() + ": " + t.getMessage());
                }
            }, null);
        }
    }

    @Override
    public Plugin getPlugin() {
        return PluginUtil.getPlugin("Chat");
    }

    @Override
    public boolean isEnabled() {
        Plugin chat = getPlugin();
        if (chat == null || !chat.isEnabled() || !PluginUtil.pluginHookIsEnabled(chat.getName())) {
            return false;
        }
        // Confirm WpmeCore's ChannelService class is on the classpath (WpmeCore loaded)
        try {
            Class.forName(CHANNEL_SERVICE_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Look up WpmeCore's ChannelService via the Bukkit ServicesManager.
     * Returns {@code null} if the Chat addon is not installed or hasn't
     * registered the service.
     */
    private @Nullable Object lookupChannelService() {
        try {
            Class<?> serviceClass = Class.forName(CHANNEL_SERVICE_CLASS);
            RegisteredServiceProvider<?> provider = Bukkit.getServicesManager().getRegistration(serviceClass);
            return provider != null ? provider.getProvider() : null;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
