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

package github.scarsz.discordsrv.api.events;

import github.scarsz.discordsrv.api.Cancellable;
import github.scarsz.discordsrv.api.LegacyChannelProxy;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

/**
 * <p>Called after {@link DiscordGuildMessageReceivedEvent} when the message was validated as coming from a linked channel</p>
 * <p>Guaranteed to be from a linked {@link TextChannel}</p>
 */
@SuppressWarnings("LombokGetterMayBeUsed")
public class DiscordGuildMessagePreProcessEvent extends DiscordEvent<MessageReceivedEvent> implements Cancellable {

    private boolean cancelled;

    private final User author;
    private final net.dv8tion.jda.api.entities.TextChannel channel;
    private final Guild guild;
    private final Member member;
    private final Message message;

    public DiscordGuildMessagePreProcessEvent(MessageReceivedEvent jdaEvent) {
        super(jdaEvent.getJDA(), jdaEvent);
        this.author = jdaEvent.getAuthor();
        this.channel = LegacyChannelProxy.wrap(jdaEvent.getChannel().asTextChannel());
        this.guild = jdaEvent.getGuild();
        this.member = jdaEvent.getMember();
        this.message = jdaEvent.getMessage();
    }

    public boolean isCancelled() {
        return this.cancelled;
    }

    public User getAuthor() {
        return this.author;
    }

    /**
     * Returns the channel as a legacy {@code net.dv8tion.jda.api.entities.TextChannel}
     * proxy for binary compatibility with plugins compiled against JDA 4/5.
     * The proxy delegates all method calls to the real JDA 6 TextChannel.
     */
    public net.dv8tion.jda.api.entities.TextChannel getChannel() {
        return this.channel;
    }

    public Guild getGuild() {
        return this.guild;
    }

    public Member getMember() {
        return this.member;
    }

    public Message getMessage() {
        return this.message;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
