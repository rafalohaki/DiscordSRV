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

package github.scarsz.discordsrv.util;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Folia-first scheduler utility. Uses the typed Folia 26.1.2 scheduler API directly —
 * no reflection, no Bukkit {@link org.bukkit.scheduler.BukkitScheduler} fallback.
 *
 * <p>See <a href="https://jd.papermc.io/folia/26.1.2/io/papermc/paper/threadedregions/scheduler/package-summary.html">
 * Folia scheduler package javadoc</a> for the authoritative API reference.</p>
 */
public final class SchedulerUtil {

    private SchedulerUtil() {}

    /**
     * @deprecated DiscordSRV is now Folia-only. This always returns {@code true}.
     *             Remove calls to this method — the Folia schedulers are always available.
     */
    @Deprecated(since = "1.30.5", forRemoval = true)
    public static boolean isFolia() {
        return true;
    }

    // ─────────────────────────────────────────────────────────
    //  Typed scheduler accessors — cached after first lookup
    // ─────────────────────────────────────────────────────────

    private static volatile GlobalRegionScheduler globalRegionScheduler;
    private static volatile AsyncScheduler asyncScheduler;

    public static GlobalRegionScheduler getGlobalRegionScheduler() {
        GlobalRegionScheduler snapshot = globalRegionScheduler;
        if (snapshot == null) {
            synchronized (SchedulerUtil.class) {
                snapshot = globalRegionScheduler;
                if (snapshot == null) {
                    snapshot = Bukkit.getGlobalRegionScheduler();
                    globalRegionScheduler = snapshot;
                }
            }
        }
        return snapshot;
    }

    public static AsyncScheduler getAsyncScheduler() {
        AsyncScheduler snapshot = asyncScheduler;
        if (snapshot == null) {
            synchronized (SchedulerUtil.class) {
                snapshot = asyncScheduler;
                if (snapshot == null) {
                    snapshot = Bukkit.getAsyncScheduler();
                    asyncScheduler = snapshot;
                }
            }
        }
        return snapshot;
    }

    // ─────────────────────────────────────────────────────────
    //  Global region scheduler (sync-equivalent on Folia)
    // ─────────────────────────────────────────────────────────

    public static void runTask(Plugin plugin, Runnable runnable) {
        getGlobalRegionScheduler().run(plugin, task -> runnable.run());
    }

    public static void runTaskLater(Plugin plugin, Runnable runnable, long delayedTicks) {
        getGlobalRegionScheduler().runDelayed(plugin, task -> runnable.run(), delayedTicks);
    }

    public static void runTaskTimer(Plugin plugin, Runnable runnable, long initialDelayTicks, long periodTicks) {
        // Folia's GlobalRegionScheduler.runAtFixedRate throws IllegalArgumentException for delay/period < 1.
        getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                task -> runnable.run(),
                Math.max(1L, initialDelayTicks),
                Math.max(1L, periodTicks)
        );
    }

    // ─────────────────────────────────────────────────────────
    //  Async scheduler
    // ─────────────────────────────────────────────────────────

    public static void runTaskAsynchronously(Plugin plugin, Runnable runnable) {
        getAsyncScheduler().runNow(plugin, task -> runnable.run());
    }

    public static void runTaskLaterAsynchronously(Plugin plugin, Runnable runnable, long delayedTicks) {
        getAsyncScheduler().runDelayed(plugin, task -> runnable.run(), delayedTicks * 50, TimeUnit.MILLISECONDS);
    }

    public static void runTaskTimerAsynchronously(Plugin plugin, Runnable runnable, long initialDelayTicks, long periodTicks) {
        getAsyncScheduler().runAtFixedRate(
                plugin,
                task -> runnable.run(),
                initialDelayTicks * 50,
                periodTicks * 50,
                TimeUnit.MILLISECONDS
        );
    }

    // ─────────────────────────────────────────────────────────
    //  Entity scheduler (follows entity across region teleports)
    // ─────────────────────────────────────────────────────────

    public static void runTaskForPlayer(Plugin plugin, Player player, Runnable runnable) {
        EntityScheduler entityScheduler = player.getScheduler();
        entityScheduler.run(plugin, task -> runnable.run(), null);
    }

    // ─────────────────────────────────────────────────────────
    //  Cancellation
    // ─────────────────────────────────────────────────────────

    public static void cancelTasks(Plugin plugin) {
        getAsyncScheduler().cancelTasks(plugin);
        getGlobalRegionScheduler().cancelTasks(plugin);
    }
}
