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

package github.scarsz.discordsrv.listeners;

import github.scarsz.discordsrv.Debug;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.events.DiscordPrivateMessageReceivedEvent;
import github.scarsz.discordsrv.util.DiscordUtil;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class DiscordAccountLinkListener extends ListenerAdapter {

    private final ErrorHandler ignoreFailedToDeleteMessage = new ErrorHandler()
            .ignore(ErrorResponse.UNKNOWN_MESSAGE)
            .ignore(ErrorResponse.MISSING_ACCESS);

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // don't process messages sent by the bot
        if (event.getAuthor().getId().equals(event.getJDA().getSelfUser().getId())) return;

        if (event.isFromGuild()) {
            // guild messages: link channel flow (formerly onGuildMessageReceived)
            if (event.getAuthor().isBot()) return;
            TextChannel linkChannel = DiscordSRV.getPlugin().getDestinationTextChannelForGameChannelName("link");
            if (!event.getChannel().equals(linkChannel)) return;

            Message receivedMessage = event.getMessage();
            String reply = DiscordSRV.getPlugin().getAccountLinkManager().process(receivedMessage.getContentRaw(), event.getAuthor().getId());
            // Upstream issue #1862: empty messages.yml keys cause Message.reply("") to throw
            // "Provided text for message may not be empty". Skip empty replies safely.
            if (reply != null && !reply.isEmpty()) {
                int deleteSeconds = DiscordSRV.config().getIntElse("MinecraftDiscordAccountLinkedMessageDeleteSeconds", 0);
                RestAction<Message> repliedMessage = receivedMessage.reply(reply).delay(deleteSeconds, TimeUnit.SECONDS);

                repliedMessage.queue(replyMessage -> {
                    if (deleteSeconds > 0) {
                        replyMessage.delete().queue(null, ignoreFailedToDeleteMessage);
                        receivedMessage.delete().queue(null, ignoreFailedToDeleteMessage.handle(ErrorResponse.MISSING_PERMISSIONS,
                                e -> DiscordSRV.debug(Debug.ACCOUNT_LINKING, "Failed to delete " + receivedMessage.getAuthor() + "'s message in the link channel because of missing permissions.")));
                    }
                });
            }
        } else {
            // DM / private messages: PM linking flow (formerly onPrivateMessageReceived)
            DiscordSRV.api.callEvent(new DiscordPrivateMessageReceivedEvent(event));

            if (!DiscordSRV.config().getBoolean("MinecraftDiscordAccountLinkedUsePM")) return;

            String reply = DiscordSRV.getPlugin().getAccountLinkManager().process(event.getMessage().getContentRaw(), event.getAuthor().getId());
            // Upstream issue #1862: skip empty replies (operator may have blanked messages.yml keys intentionally).
            if (reply != null && !reply.isEmpty()) event.getMessage().reply(reply).queue();
        }
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        Member member = event.getMember();

        if (DiscordSRV.getPlugin().getAccountLinkManager() == null) {
            DiscordSRV.debug(Debug.ACCOUNT_LINKING, "AccountLinkManager is null, not processing join event");
            return;
        }

        // add linked role and nickname back to people when they rejoin the server
        UUID uuid = DiscordSRV.getPlugin().getAccountLinkManager().getUuid(event.getUser().getId());
        if (uuid != null) {
            Role roleToAdd = DiscordUtil.resolveRole(DiscordSRV.config().getString("MinecraftDiscordAccountLinkedRoleNameToAddUserTo"));
            if (roleToAdd == null || roleToAdd.getGuild().equals(member.getGuild())) {
                if (roleToAdd != null) DiscordUtil.addRoleToMember(member, roleToAdd);
                else DiscordSRV.debug(Debug.GROUP_SYNC, "Couldn't add user to null role");
            } else {
                DiscordSRV.debug(Debug.GROUP_SYNC, "Not adding role to member upon guild join due to the guild being different! (" + roleToAdd.getGuild() + " / " + member.getGuild() + ")");
            }

            if (DiscordSRV.config().getBoolean("NicknameSynchronizationEnabled")) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                DiscordSRV.getPlugin().getNicknameUpdater().setNickname(member, player);
            }
        }
    }

}
