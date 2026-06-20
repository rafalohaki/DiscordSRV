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

package github.scarsz.discordsrv.api;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Creates dynamic proxies that implement the legacy JDA 4/5 channel
 * interfaces ({@code net.dv8tion.jda.api.entities.TextChannel} /
 * {@code PrivateChannel}) while delegating all method calls to a real
 * JDA 6 channel instance.
 *
 * <p>This bridges the binary-compatibility gap for downstream plugins
 * (e.g. CMI) that were compiled against DiscordSRV's old JDA 4/5 API.
 * After Shadow relocation, the proxy implements
 * {@code github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel}
 * — the exact type CMI's bytecode expects from
 * {@code DiscordGuildMessagePreProcessEvent#getChannel()}.
 *
 * <p>All interface methods (sendMessage, getId, getName, getGuild, …)
 * are forwarded to the delegate. {@code equals}, {@code hashCode}, and
 * {@code toString} are also forwarded so the proxy behaves like the
 * underlying channel.
 */
public final class LegacyChannelProxy implements InvocationHandler {

    private final Object delegate;

    private LegacyChannelProxy(Object delegate) {
        this.delegate = delegate;
    }

    /**
     * Wraps a JDA 6 {@code TextChannel} in a proxy implementing the
     * legacy {@code net.dv8tion.jda.api.entities.TextChannel} shim.
     *
     * @param channel the JDA 6 TextChannel (may be null)
     * @return a proxy implementing the legacy interface, or null if input is null
     */
    public static net.dv8tion.jda.api.entities.TextChannel wrap(
            net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel) {
        if (channel == null) return null;
        return (net.dv8tion.jda.api.entities.TextChannel) Proxy.newProxyInstance(
                LegacyChannelProxy.class.getClassLoader(),
                new Class<?>[]{net.dv8tion.jda.api.entities.TextChannel.class},
                new LegacyChannelProxy(channel)
        );
    }

    /**
     * Wraps a JDA 6 {@code PrivateChannel} in a proxy implementing the
     * legacy {@code net.dv8tion.jda.api.entities.PrivateChannel} shim.
     *
     * @param channel the JDA 6 PrivateChannel (may be null)
     * @return a proxy implementing the legacy interface, or null if input is null
     */
    public static net.dv8tion.jda.api.entities.PrivateChannel wrap(
            net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel channel) {
        if (channel == null) return null;
        return (net.dv8tion.jda.api.entities.PrivateChannel) Proxy.newProxyInstance(
                LegacyChannelProxy.class.getClassLoader(),
                new Class<?>[]{net.dv8tion.jda.api.entities.PrivateChannel.class},
                new LegacyChannelProxy(channel)
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            // All methods — including equals/hashCode/toString from Object —
            // delegate to the real JDA 6 channel. Method.invoke works because
            // the delegate implements every superinterface of the shim
            // (TextChannel, MessageChannel, GuildChannel, Channel, …).
            return method.invoke(delegate, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
