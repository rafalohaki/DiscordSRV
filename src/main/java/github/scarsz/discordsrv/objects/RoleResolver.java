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
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Selects which Discord roles to display for a member in bridged chat messages.
 *
 * <p>Supports whitelist mode ({@code DiscordChatChannelRolesSelectionAsWhitelist=true})
 * and blacklist mode (default). Extracted from {@link DiscordSRV} to isolate
 * the filtering logic that was a source of confusion and bugs.
 */
public final class RoleResolver {

    private RoleResolver() {}

    public static List<Role> getSelectedRoles(Member member) {
        List<String> discordRolesSelection = DiscordSRV.config().getStringList("DiscordChatChannelRolesSelection");
        List<Role> selectedRoles;
        if (DiscordSRV.config().getBoolean("DiscordChatChannelRolesSelectionAsWhitelist")) {
            // whitelist: only keep roles that are in the selection list
            selectedRoles = member.getRoles().stream()
                    .filter(role -> discordRolesSelection.contains(DiscordUtil.getRoleName(role)) || discordRolesSelection.contains(role.getId()))
                    .collect(Collectors.toList());
        } else {
            // blacklist: keep roles that are NOT in the selection list
            selectedRoles = member.getRoles().stream()
                    .filter(role -> !(discordRolesSelection.contains(DiscordUtil.getRoleName(role)) || discordRolesSelection.contains(role.getId())))
                    .collect(Collectors.toList());
        }
        selectedRoles.removeIf(role -> StringUtils.isBlank(role.getName()));
        return selectedRoles;
    }

    public static Role getTopSelectedRole(Member member) {
        List<Role> selectedRoles = getSelectedRoles(member);
        if (selectedRoles.isEmpty()) return null;
        // member.getRoles() is ordered by position (highest first); return the
        // first one that's also in the selected set
        return member.getRoles().stream()
                .filter(selectedRoles::contains)
                .findFirst().orElse(null);
    }
}
