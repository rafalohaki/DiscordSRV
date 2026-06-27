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

package github.scarsz.discordsrv.commands;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.objects.managers.AccountLinkManager;

import net.dv8tion.jda.api.entities.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import github.scarsz.discordsrv.util.DiscordUtil;
import github.scarsz.discordsrv.util.GamePermissionUtil;
import github.scarsz.discordsrv.util.LangUtil;
import github.scarsz.discordsrv.util.MessageUtil;
import github.scarsz.discordsrv.util.PlaceholderUtil;
import github.scarsz.discordsrv.util.SchedulerUtil;

public class CommandLink {

    @Command(commandNames = { "link" },
            helpMessage = "Generates a code to link your Minecraft account to your Discord account",
            permission = "discordsrv.link"
    )
    public static void execute(CommandSender sender, String[] args) {
        AccountLinkManager manager = DiscordSRV.getPlugin().getAccountLinkManager();
        if (manager == null) {
            MessageUtil.sendMessage(sender, LangUtil.Message.UNABLE_TO_LINK_ACCOUNTS_RIGHT_NOW.toString());
            return;
        }

        SchedulerUtil.runTaskAsynchronously(DiscordSRV.getPlugin(), () -> executeAsync(sender, args, manager));
    }

    @SuppressWarnings({"deprecation", "ConstantConditions"})
    private static void executeAsync(CommandSender sender, String[] args, AccountLinkManager manager) {
        // assume manual link
        if (args.length >= 2) {
            if (!GamePermissionUtil.hasPermission(sender, "discordsrv.link.others")) {
                sender.sendMessage(LangUtil.Message.NO_PERMISSION.toString());
                return;
            }

            List<String> arguments = new ArrayList<>(Arrays.asList(args));
            String minecraft = arguments.remove(0);
            String discord = String.join(" ", arguments);

            OfflinePlayer offlinePlayer = null;

            try {
                offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(minecraft));
            } catch (IllegalArgumentException ignored) {}

            if (offlinePlayer == null) offlinePlayer = Bukkit.getOfflinePlayer(minecraft);
            if (offlinePlayer == null) {
                MessageUtil.sendMessage(sender, Component.text("Minecraft player could not be found", NamedTextColor.RED));
                return;
            }

            User user = null;
            try {
                user = DiscordUtil.getJda().getUserById(discord);
            } catch (IllegalArgumentException ignored) {}

            if (user == null) {
                try {
                    user = DiscordUtil.getJda().getUserByTag(discord);
                } catch (IllegalArgumentException ignored) {}
            }

            if (user == null) {
                MessageUtil.sendMessage(sender, Component.text("Discord user could not be found", NamedTextColor.RED));
                return;
            }

            DiscordSRV.getPlugin().getAccountLinkManager().link(user.getId(), offlinePlayer.getUniqueId());
            MessageUtil.sendMessage(sender, Component.text("Linked together ", NamedTextColor.GREEN).append(Component.text(offlinePlayer.getName(), NamedTextColor.GOLD))
                    .append(Component.text(" and ", NamedTextColor.GREEN)).append(Component.text(user.getAsTag(), NamedTextColor.GOLD)));
            return;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text(LangUtil.InternalMessage.PLAYER_ONLY_COMMAND.toString(), NamedTextColor.RED));
            return;
        }
        Player player = (Player) sender;

        // prevent people from generating multiple link codes then claiming them all at once to get multiple rewards
        new ArrayList<>(manager.getLinkingCodes().entrySet()).stream()
                .filter(entry -> entry.getValue().equals(player.getUniqueId()))
                .forEach(match -> manager.getLinkingCodes().remove(match.getKey()));

        if (manager.getDiscordId(player.getUniqueId()) != null) {
            MessageUtil.sendMessage(sender, LangUtil.Message.ACCOUNT_ALREADY_LINKED.toString());
        } else {
            String code = manager.generateCode(player.getUniqueId());

            // load message text
            String message = LangUtil.Message.CODE_GENERATED.toString()
                    .replace("%code%", code)
                    .replace("%botname%", DiscordSRV.getPlugin().getMainGuild().getSelfMember().getEffectiveName());
            // replace additional placeholders (PlaceholderAPI)
            message = PlaceholderUtil.replacePlaceholders(message, Bukkit.getOfflinePlayer(player.getUniqueId()));
            // build message component
            Component component = LegacyComponentSerializer.builder().character('&').extractUrls().build().deserialize(message);

            String clickToCopyCode = LangUtil.Message.CLICK_TO_COPY_CODE.toString();
            if (StringUtils.isNotBlank(clickToCopyCode)) {
                component = component.clickEvent(ClickEvent.copyToClipboard(code))
                        .hoverEvent(HoverEvent.showText(
                                LegacyComponentSerializer.legacy('&').deserialize(clickToCopyCode)
                        ));
            }

            MessageUtil.sendMessage(sender, component);
        }
    }

}
