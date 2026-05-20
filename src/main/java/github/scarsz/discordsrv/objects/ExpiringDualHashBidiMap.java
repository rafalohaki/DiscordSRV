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

import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class ExpiringDualHashBidiMap<K, V> extends DualHashBidiMap<K, V> {

    // ConcurrentHashMap drops the per-call synchronized blocks and avoids virtual-thread pinning on Java 21.
    private final Map<K, Long> expiryTimes = new ConcurrentHashMap<>();
    // DualHashBidiMap is NOT thread-safe — we must guard every super-call below. ReentrantLock
    // instead of synchronized so that virtual threads do not get pinned to their carrier under load
    // (matches the project-wide threading rule in CLAUDE.md).
    final ReentrantLock collectionLock = new ReentrantLock();
    private final long expiryDelay;

    public ExpiringDualHashBidiMap(long expiryDelayMillis) {
        this.expiryDelay = expiryDelayMillis;
        ExpiryThread.references.add(new WeakReference<>(this));
    }

    @Override
    public V put(K key, V value) {
        collectionLock.lock();
        try {
            expiryTimes.put(key, System.currentTimeMillis() + expiryDelay);
            return super.put(key, value);
        } finally {
            collectionLock.unlock();
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public V putNotExpiring(K key, V value) {
        collectionLock.lock();
        try {
            return super.put(key, value);
        } finally {
            collectionLock.unlock();
        }
    }

    public V putExpiring(K key, V value, long expiryTime) {
        if (expiryTime < System.currentTimeMillis()) throw new IllegalArgumentException("The expiry time must be in the future");
        collectionLock.lock();
        try {
            expiryTimes.put(key, expiryTime);
            return super.put(key, value);
        } finally {
            collectionLock.unlock();
        }
    }

    @Override
    public V remove(Object key) {
        collectionLock.lock();
        try {
            expiryTimes.remove(key);
            return super.remove(key);
        } finally {
            collectionLock.unlock();
        }
    }

    @Override
    public K removeValue(Object value) {
        collectionLock.lock();
        try {
            K key = getKey(value);
            if (key != null) {
                expiryTimes.remove(key);
            }
            return super.removeValue(value);
        } finally {
            collectionLock.unlock();
        }
    }

    public long getExpiryTime(K key) {
        if (!containsKey(key)) throw new IllegalArgumentException("The given key is not in the map");
        return expiryTimes.get(key);
    }

    public void setExpiryTime(K key, long expiryTimeMillis) {
        if (!containsKey(key)) throw new IllegalArgumentException("The given key is not in the map");
        expiryTimes.put(key, expiryTimeMillis);
    }

    public long getExpiryDelay() {
        return expiryDelay;
    }

    @SuppressWarnings({"SuspiciousMethodCalls"})
    private void keyExpired(Object key) {
        // Caller holds collectionLock — uses super.remove directly to avoid taking the lock twice.
        expiryTimes.remove(key);
        super.remove(key);
    }

    public static class ExpiryThread extends Thread {

        private static final Set<WeakReference<ExpiringDualHashBidiMap<?, ?>>> references = ConcurrentHashMap.newKeySet();

        private ExpiryThread() {
            super("DiscordSRV " + ExpiryThread.class.getSimpleName());
            setDaemon(true);
            Runtime.getRuntime().addShutdownHook(new Thread(this::interrupt, "DiscordSRV ExpiryThread shutdown hook"));
        }

        @Override
        public void run() {
            while (!isInterrupted()) {
                long currentTime = System.currentTimeMillis();
                for (WeakReference<ExpiringDualHashBidiMap<?, ?>> reference : references) {
                    final ExpiringDualHashBidiMap<?, ?> collection = reference.get();
                    if (collection == null) {
                        references.remove(reference);
                        continue;
                    }
                    // expiryTimes is a ConcurrentHashMap — safe to iterate without external locking.
                    List<Object> removals = new ArrayList<>();
                    collection.expiryTimes.forEach((key, value) -> {
                        if (value < currentTime) removals.add(key);
                    });
                    if (removals.isEmpty()) continue;
                    // DualHashBidiMap parent is not thread-safe — ReentrantLock keeps the critical
                    // section consistent with the rest of the public API (put/remove/...).
                    collection.collectionLock.lock();
                    try {
                        removals.forEach(collection::keyExpired);
                    } finally {
                        collection.collectionLock.unlock();
                    }
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        }

        static {
            new ExpiryThread().start();
        }

    }

}
