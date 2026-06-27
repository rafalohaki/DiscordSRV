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
import github.scarsz.discordsrv.util.PluginUtil;
import github.scarsz.discordsrv.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ChatHook for the WpmeCore Chat addon.
 *
 * <p>Discord → Minecraft: {@link #broadcastMessageToChannel} delegates to the
 * default {@link ChatHook} pipeline, which delivers the formatted component to
 * each online player via {@code player.getScheduler().run()} (Folia-safe).
 * Recipients are resolved from WpmeCore's {@code ChannelService} when the
 * channel name maps to a configured WpmeCore channel; otherwise all online
 * players receive the message (matching DiscordSRV's no-hook fallback, but
 * routed through the hook so DiscordSRV delegates instead of using its own
 * broadcast path).
 *
 * <p>Minecraft → Discord: handled by the Chat addon's own
 * {@code DiscordSrvBridge} (listens to {@code AsyncChatEvent} and calls
 * {@code processChatMessage}). This hook does NOT register an
 * {@code @EventHandler} for {@code AsyncChatEvent} to avoid double-forwarding.
 *
 * <p>Soft-dep: uses the Bukkit {@code ServicesManager} to look up
 * {@code ChannelService} at runtime — no compile-time dependency on WpmeCore.
 */
public class WpmeCoreChatHook implements ChatHook {

    /** WpmeCore ChannelService class name (looked up via ServicesManager). */
    private static final String CHANNEL_SERVICE_CLASS = "org.rafalohaki.wpmecore.api.channel.ChannelService";

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
                Object channelOpt = channelService.getClass().getMethod("channelById", String.class)
                        .invoke(channelService, channelName);
                if (channelOpt != null && (boolean) channelOpt.getClass().getMethod("isPresent").invoke(channelOpt)) {
                    Object channel = channelOpt.getClass().getMethod("get").invoke(channelOpt);
                    @SuppressWarnings("unchecked")
                    List<UUID> memberUuids = (List<UUID>) channelService.getClass()
                            .getMethod("membersOf", String.class).invoke(channelService, channelName);
                    List<Player> recipients = new ArrayList<>();
                    for (UUID uuid : memberUuids) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            recipients.add(p);
                        }
                    }
                    String channelId = (String) channel.getClass().getMethod("id").invoke(channel);
                    return new ChannelInfo(channelId != null ? channelId : channelName, null, "", recipients);
                }
            } catch (Throwable t) {
                DiscordSRV.debug(Debug.DISCORD_TO_MINECRAFT,
                        "WpmeCoreChatHook: failed to resolve channel \"" + channelName + "\" via ChannelService: " + t.getMessage());
            }
        }

        // Unknown channel — fall back to all online players rather than dropping
        // the message. This matches DiscordSRV's no-hook behaviour and ensures
        // no Discord message is silently lost.
        return globalChannelInfo();
    }

    private @NotNull ChannelInfo globalChannelInfo() {
        List<Player> recipients = new ArrayList<>(PlayerUtil.getOnlinePlayers(false));
        return new ChannelInfo("Global", null, "", recipients);
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
