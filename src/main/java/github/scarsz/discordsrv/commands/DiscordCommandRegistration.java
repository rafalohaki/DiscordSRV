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

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.listeners.DiscordDisconnectListener;
import github.scarsz.discordsrv.objects.managers.CommandManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.dv8tion.jda.api.requests.CloseCode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Brigadier registration for the {@code /discord} command tree.
 *
 * <p>Replaces the legacy {@code plugin.yml} {@code commands:} section +
 * {@code DiscordSRV.onCommand()} override. All subcommands are registered
 * as Brigadier literals that delegate to the existing {@link CommandManager}
 * via reflection (same {@code @Command} annotated methods).
 *
 * <p>The root {@code /discord} (no args) shows the invite link, matching
 * the previous {@code commandManager.handle(sender, null, ...)} behavior.
 * Each subcommand accepts an optional greedy-string argument that is split
 * into words and passed as {@code String[] args} to the command method.
 */
public final class DiscordCommandRegistration {

    private DiscordCommandRegistration() {}

    /**
     * Registers the {@code /discord} command tree with alias
     * {@code discordsrv}.
     *
     * @param registrar the Brigadier registrar from
     *                  {@code LifecycleEvents.COMMANDS} event
     */
    public static void register(Commands registrar) {
        CommandManager cm = DiscordSRV.getPlugin().getCommandManager();

        var root = Commands.literal("discord")
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!DiscordSRV.getPlugin().isEnabled()) {
                        sendDisabledMessage(sender);
                        return 1;
                    }
                    cm.handle(sender, null, new String[0]);
                    return 1;
                });

        // Add each subcommand as a literal node.
        // The CommandManager maps every alias (e.g. "?" and "help") to the
        // same method, so we iterate the map and register each key separately.
        for (Map.Entry<String, Method> entry : cm.getCommands().entrySet()) {
            String name = entry.getKey();
            Command cmd = entry.getValue().getAnnotation(Command.class);
            if (cmd == null) continue;

            var sub = Commands.literal(name)
                    .requires(src -> src.getSender().hasPermission(cmd.permission()))
                    // No args path
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        if (!DiscordSRV.getPlugin().isEnabled()
                                && !name.equalsIgnoreCase("debug")) {
                            sendDisabledMessage(sender);
                            return 1;
                        }
                        cm.handle(sender, name, new String[0]);
                        return 1;
                    })
                    // Greedy args path — split into words for the command method
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                CommandSender sender = ctx.getSource().getSender();
                                if (!DiscordSRV.getPlugin().isEnabled()
                                        && !name.equalsIgnoreCase("debug")) {
                                    sendDisabledMessage(sender);
                                    return 1;
                                }
                                String raw = StringArgumentType.getString(ctx, "args");
                                String[] args = raw.isEmpty() ? new String[0] : raw.split("\\s+");
                                cm.handle(sender, name, args);
                                return 1;
                            }));

            root.then(sub);
        }

        LiteralCommandNode<CommandSourceStack> built = root.build();
        registrar.register(built, "DiscordSRV commands", List.of("discordsrv"));
    }

    /**
     * Sends an appropriate error message when DiscordSRV is disabled.
     * Replaces the legacy {@code onCommand} disabled-check with Adventure
     * Component (no ChatColor).
     */
    private static void sendDisabledMessage(CommandSender sender) {
        DiscordSRV plugin = DiscordSRV.getPlugin();
        if (plugin.invalidBotToken) {
            sender.sendMessage(Component.text("DiscordSRV is disabled: your bot token is invalid.", NamedTextColor.RED));
            sender.sendMessage(Component.text("Please enter a valid token into your config.yml ", NamedTextColor.RED)
                    .append(Component.text("(/plugins/DiscordSRV/config.yml)", NamedTextColor.GRAY))
                    .append(Component.text(" and restart your server to get DiscordSRV to work.", NamedTextColor.RED)));
        } else if (DiscordDisconnectListener.mostRecentCloseCode == CloseCode.DISALLOWED_INTENTS) {
            sender.sendMessage(Component.text("DiscordSRV is disabled: your DiscordSRV bot is lacking required intents.", NamedTextColor.RED));
            sender.sendMessage(Component.text("Please check your server log ", NamedTextColor.RED)
                    .append(Component.text("(/logs/latest.log)", NamedTextColor.GRAY))
                    .append(Component.text(" for an extended error message during DiscordSRV's startup to get DiscordSRV to work.", NamedTextColor.RED)));
        } else {
            sender.sendMessage(Component.text("DiscordSRV is disabled, check your server log ", NamedTextColor.RED)
                    .append(Component.text("(/logs/latest.log)", NamedTextColor.GRAY))
                    .append(Component.text(" for errors during DiscordSRV's startup to find out why", NamedTextColor.RED)));
        }
    }
}
