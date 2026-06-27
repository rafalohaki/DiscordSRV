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

package github.scarsz.discordsrv.objects.proxy;

import dev.vankka.dynamicproxy.processor.Original;
import dev.vankka.dynamicproxy.processor.Proxy;
import github.scarsz.discordsrv.util.DiscordChatChannelCommandFeedbackForwarder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Dynamic proxy for {@link CommandSender} that intercepts messages sent to
 * the console (when Discord console channel is enabled) and forwards them
 * to Discord.
 *
 * <p>Only String-based messages are intercepted. Component-based messages
 * (Adventure API) are handled natively by Folia's CommandSender which
 * implements {@code Audience} — no proxy interception needed.
 */
@Proxy(CommandSender.class)
public abstract class CommandSenderDynamic implements CommandSender {

    @Original
    private final CommandSender original;
    private final DiscordChatChannelCommandFeedbackForwarder sendUtil;

    public CommandSenderDynamic(CommandSender original, MessageReceivedEvent event) {
        this.original = original;
        this.sendUtil = new DiscordChatChannelCommandFeedbackForwarder(event);
    }

    private void doSend(String message) {
        sendUtil.send(message);
    }

    @Override
    public void sendMessage(@NotNull String s) {
        original.sendMessage(s);
        doSend(s);
    }

    @Override
    public void sendMessage(@NotNull String[] strings) {
        original.sendMessage(strings);
        for (String string : strings) {
            doSend(string);
        }
    }

    @Override
    public void sendMessage(@Nullable UUID uuid, @NotNull String s) {
        original.sendMessage(s);
        doSend(s);
    }

    @Override
    public void sendMessage(@Nullable UUID uuid, @NotNull String[] strings) {
        original.sendMessage(strings);
        for (String string : strings) {
            doSend(string);
        }
    }
}
