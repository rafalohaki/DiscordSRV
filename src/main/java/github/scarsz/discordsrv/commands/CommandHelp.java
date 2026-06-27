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
import github.scarsz.discordsrv.util.GamePermissionUtil;
import github.scarsz.discordsrv.util.LangUtil;
import github.scarsz.discordsrv.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;

import java.lang.reflect.Method;

import java.util.concurrent.ThreadLocalRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CommandHelp {

    private static final List<NamedTextColor> allNamedTextColors = Arrays.asList(
            NamedTextColor.BLACK, NamedTextColor.DARK_BLUE, NamedTextColor.DARK_GREEN,
            NamedTextColor.DARK_AQUA, NamedTextColor.DARK_RED, NamedTextColor.DARK_PURPLE,
            NamedTextColor.GOLD, NamedTextColor.GRAY, NamedTextColor.DARK_GRAY,
            NamedTextColor.BLUE, NamedTextColor.GREEN, NamedTextColor.AQUA,
            NamedTextColor.RED, NamedTextColor.LIGHT_PURPLE, NamedTextColor.YELLOW,
            NamedTextColor.WHITE
    );

    private static final Set<NamedTextColor> disallowedChatColorCharacters = new HashSet<>(Arrays.asList(
            NamedTextColor.BLACK,
            NamedTextColor.DARK_BLUE,
            NamedTextColor.GRAY,
            NamedTextColor.DARK_GRAY,
            NamedTextColor.WHITE
    ));

    @Command(commandNames = { "?", "help" },
            helpMessage = "Shows command help for DiscordSRV's commands",
            permission = "discordsrv.help",
            usageExample = "help [command]"
    )
    public static void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            help(sender);
        } else {
            help(sender, Arrays.asList(args));
        }
    }

    private static void help(CommandSender sender) {
        NamedTextColor titleColor = NamedTextColor.BLACK, commandColor = NamedTextColor.BLACK;
        while (disallowedChatColorCharacters.contains(titleColor))
            titleColor = allNamedTextColors.get(ThreadLocalRandom.current().nextInt(allNamedTextColors.size()));
        while (disallowedChatColorCharacters.contains(commandColor) || commandColor == titleColor)
            commandColor = allNamedTextColors.get(ThreadLocalRandom.current().nextInt(allNamedTextColors.size()));

        List<Method> commandMethods = new ArrayList<>();
        for (Method method : DiscordSRV.getPlugin().getCommandManager().getCommands().values())
            if (!commandMethods.contains(method)) commandMethods.add(method);

        MessageUtil.sendMessage(sender, Component.text("================[ ", NamedTextColor.DARK_GRAY).append(Component.text("DiscordSRV", titleColor)).append(Component.text(" ]================", NamedTextColor.DARK_GRAY)));
        for (Method commandMethod : commandMethods) {
            Command commandAnnotation = commandMethod.getAnnotation(Command.class);

            // make sure sender has permission to run the commands before showing them permissions for it
            if (!GamePermissionUtil.hasPermission(sender, commandAnnotation.permission())) continue;

            MessageUtil.sendMessage(sender, Component.text("- ", NamedTextColor.GRAY).append(Component.text("/discord " + String.join("/", commandAnnotation.commandNames()), commandColor)));
            MessageUtil.sendMessage(sender, Component.text("    ").append(Component.text(commandAnnotation.helpMessage()).decorate(TextDecoration.ITALIC)));
            if (!commandAnnotation.usageExample().equals("")) MessageUtil.sendMessage(sender, Component.text("    ").append(Component.text("ex. /discord " + commandAnnotation.usageExample(), NamedTextColor.GRAY).decorate(TextDecoration.ITALIC)));
        }
    }

    /**
     * Send help specific for the given commands
     * @param sender
     * @param commands
     */
    private static void help(CommandSender sender, List<String> commands) {
        NamedTextColor titleColor = NamedTextColor.BLACK, commandColor = NamedTextColor.BLACK;
        while (disallowedChatColorCharacters.contains(titleColor))
            titleColor = allNamedTextColors.get(ThreadLocalRandom.current().nextInt(allNamedTextColors.size()));
        while (disallowedChatColorCharacters.contains(commandColor) || commandColor == titleColor)
            commandColor = allNamedTextColors.get(ThreadLocalRandom.current().nextInt(allNamedTextColors.size()));

        List<Method> commandMethodsList = new LinkedList<>();
        Map<String, Method> commandMethods = DiscordSRV.getPlugin().getCommandManager().getCommands();
        for (String commandName : commands) {
            if (commandMethods.containsKey(commandName)) {
                commandMethodsList.add(DiscordSRV.getPlugin().getCommandManager().getCommands().get(commandName));
            }
        }

        if (commandMethodsList.isEmpty()) {
            MessageUtil.sendMessage(sender, LangUtil.Message.COMMAND_DOESNT_EXIST.toString());
            return;
        }

        MessageUtil.sendMessage(sender, Component.text("===================[ ", NamedTextColor.DARK_GRAY).append(Component.text("DiscordSRV", titleColor)).append(Component.text(" ]===================", NamedTextColor.DARK_GRAY)));
        for (Method commandMethod : commandMethodsList) {
            Command commandAnnotation = commandMethod.getAnnotation(Command.class);

            // make sure sender has permission to run the commands before showing them permissions for it
            if (!GamePermissionUtil.hasPermission(sender, commandAnnotation.permission())) continue;

            MessageUtil.sendMessage(sender, Component.text("- ", NamedTextColor.GRAY).append(Component.text("/discord " + String.join("/", commandAnnotation.commandNames()), commandColor)));
            MessageUtil.sendMessage(sender, Component.text("   ").append(Component.text(commandAnnotation.helpMessage()).decorate(TextDecoration.ITALIC)));
            if (!commandAnnotation.usageExample().equals("")) MessageUtil.sendMessage(sender, Component.text("   ").append(Component.text("ex. /discord " + commandAnnotation.usageExample(), NamedTextColor.GRAY).decorate(TextDecoration.ITALIC)));
        }
    }

}
