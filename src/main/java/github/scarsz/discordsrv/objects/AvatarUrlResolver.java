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
import github.scarsz.discordsrv.util.NMSUtil;
import github.scarsz.discordsrv.util.PlaceholderUtil;
import github.scarsz.discordsrv.util.PlayerUtil;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.UUID;

/**
 * Resolves avatar URLs for Minecraft players when delivering messages to Discord
 * via webhooks. Supports crafthead.net, minotar.net, and any custom URL template
 * configured via the {@code AvatarUrl} config option.
 *
 * <p>Extracted from {@link DiscordSRV} to isolate URL construction logic and
 * the offline-mode / Geyser edge cases that were a frequent source of bugs.
 */
public final class AvatarUrlResolver {

    private AvatarUrlResolver() {}

    private static boolean offlineUuidAvatarUrlNagged = false;

    public static String getAvatarUrl(String username, UUID uuid) {
        String avatarUrl = constructAvatarUrl(username, uuid, "");
        return PlaceholderUtil.replacePlaceholdersToDiscord(avatarUrl);
    }

    public static String getAvatarUrl(OfflinePlayer player) {
        if (player.isOnline()) {
            return getAvatarUrl(player.getPlayer());
        }
        String avatarUrl = constructAvatarUrl(player.getName(), player.getUniqueId(), "");
        return PlaceholderUtil.replacePlaceholdersToDiscord(avatarUrl, player);
    }

    public static String getAvatarUrl(Player player) {
        String avatarUrl = constructAvatarUrl(player.getName(), player.getUniqueId(), NMSUtil.getTexture(player));
        return PlaceholderUtil.replacePlaceholdersToDiscord(avatarUrl, player);
    }

    @SuppressWarnings("deprecation")
    private static String constructAvatarUrl(String username, UUID uuid, String texture) {
        boolean offline = uuid == null || PlayerUtil.uuidIsOffline(uuid);
        OfflinePlayer player = null;
        if (StringUtils.isNotBlank(username) && offline) {
            player = Bukkit.getOfflinePlayer(username);
            uuid = player.getUniqueId();
            offline = PlayerUtil.uuidIsOffline(uuid);
        }
        if (StringUtils.isBlank(username) && uuid != null) {
            player = Bukkit.getOfflinePlayer(uuid);
            username = player.getName();
        }
        if (StringUtils.isBlank(texture) && player != null && player.isOnline()) {
            texture = NMSUtil.getTexture(player.getPlayer());
        }

        String avatarUrl = DiscordSRV.config().getString("AvatarUrl");
        String defaultUrl = "https://crafthead.net/helm/{uuid-nodashes}/{size}#{texture}";
        String offlineUrl = "https://crafthead.net/helm/{username}/{size}#{texture}";

        if (StringUtils.isBlank(avatarUrl)) {
            avatarUrl = !offline ? defaultUrl : offlineUrl;
        }

        if (avatarUrl.contains("://crafatar.com/")) {
            avatarUrl = !offline ? defaultUrl : offlineUrl;
            DiscordSRV.config().setRuntimeValue("AvatarUrl", avatarUrl);
            DiscordSRV.warning("Your AvatarUrl config option uses crafatar.com, which no longer allows usage with Discord. An alternative provider will be used.");
            DiscordSRV.warning("You should set your AvatarUrl (in config.yml) to an empty string (\"\") to get rid of this warning.");
        }

        if (offline && (avatarUrl.contains("{uuid}") || avatarUrl.contains("{uuid-nodashes}")) && !offlineUuidAvatarUrlNagged) {
            DiscordSRV.error("Your AvatarUrl config option contains {uuid} or {uuid-nodashes} but this server is using offline UUIDs.");
            offlineUuidAvatarUrlNagged = true;
        }

        if (username.startsWith("*")) {
            // geyser adds * to beginning of its usernames
            username = username.substring(1);
        }
        try {
            username = URLEncoder.encode(username, "utf8");
        } catch (UnsupportedEncodingException ignored) {}

        String usedBaseUrl = avatarUrl;
        avatarUrl = avatarUrl
                .replace("{texture}", texture != null ? texture : "")
                .replace("{username}", username)
                .replace("{uuid}", uuid != null ? uuid.toString() : "")
                .replace("{uuid-nodashes}", uuid.toString().replace("-", ""))
                .replace("{size}", "128");

        DiscordSRV.debug("Constructed avatar url: " + avatarUrl + " from " + usedBaseUrl);
        DiscordSRV.debug("Avatar url is for " + (offline ? "**offline** " : "") + "uuid: " + uuid + ". The texture is: " + texture);

        return avatarUrl;
    }
}
