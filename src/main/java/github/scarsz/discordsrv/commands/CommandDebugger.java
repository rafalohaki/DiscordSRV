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

import github.scarsz.discordsrv.Debug;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.util.DebugUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CommandDebugger {

    @Command(commandNames = { "debugger" },
            helpMessage = "A toggleable timings-like command to dump debug information to bin.scarsz.me",
            permission = "discordsrv.debug"
    )
    public static void execute(CommandSender sender, String[] args) {
        List<String> arguments = new ArrayList<>(Arrays.asList(args));

        String subCommand;
        if (arguments.isEmpty()) {
            subCommand = "start";
        } else {
            subCommand = arguments.remove(0);
        }

        boolean upload = false;
        if (subCommand.equalsIgnoreCase("start") || subCommand.equalsIgnoreCase("on")) {
            Set<String> validArguments = new HashSet<>();
            for (String argument : arguments) {
                boolean anyValid = false;
                for (Debug value : Debug.values()) {
                    if (value.matches(argument)) {
                        anyValid = true;
                        break;
                    }
                }
                if (!anyValid) {
                    sender.sendMessage(Component.text("Invalid debug category: ", NamedTextColor.RED).append(Component.text(argument, NamedTextColor.DARK_RED)));
                    continue;
                }

                validArguments.add(argument);
            }

            if (validArguments.isEmpty()) {
                DiscordSRV.getPlugin().getDebuggerCategories().add(Debug.UNCATEGORIZED.name());
            } else {
                DiscordSRV.getPlugin().getDebuggerCategories().addAll(validArguments);
            }
            sender.sendMessage(Component.text("Debugger enabled, use ", NamedTextColor.DARK_AQUA)
                    .append(Component.text("/discordsrv debugger stop ", NamedTextColor.GRAY))
                    .append(Component.text("to stop debugging or ", NamedTextColor.DARK_AQUA))
                    .append(Component.text("/discordsrv debugger upload ", NamedTextColor.GRAY))
                    .append(Component.text("to stop debugging and generate a debug report", NamedTextColor.DARK_AQUA)));
            return;
        } else if (subCommand.equalsIgnoreCase("stop") || subCommand.equalsIgnoreCase("off")
                || (upload = subCommand.equalsIgnoreCase("upload"))) {
            if (upload) {
                String result = DebugUtil.run(sender instanceof ConsoleCommandSender ? "CONSOLE" : sender.getName(), arguments.size() == 0 ? 256 : Integer.parseInt(arguments.get(0)));
                sender.sendMessage(Component.text("Your debug report has been generated and is available at ", NamedTextColor.DARK_AQUA).append(Component.text(result, NamedTextColor.AQUA)));
            } else {
                sender.sendMessage(Component.text("Debugger disabled", NamedTextColor.DARK_AQUA));
            }
            DiscordSRV.getPlugin().getDebuggerCategories().clear();
            return;
        }

        sender.sendMessage(Component.text("Invalid subcommand ", NamedTextColor.RED).append(Component.text(subCommand, NamedTextColor.DARK_RED)));
    }

}
