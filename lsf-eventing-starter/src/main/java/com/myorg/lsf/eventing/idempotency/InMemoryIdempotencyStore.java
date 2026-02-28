package com.myorg.lsf.eventing.idempotency;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
//Lưu trạng thái vào RAM (ConcurrentHashMap) dùng cho môi trường dev hoặc app chỉ có 1 instance.
// Có thread chạy ngầm (cleaner) tự động dọn dẹp bộ nhớ chống tràn RAM.
@Slf4j
public class InMemoryIdempotencyStore implements IdempotencyStore, AutoCloseable {

    private enum State { PROCESSING, DONE }
    private record Entry(State state, long expireAtMs, String token) {}

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    private final Duration doneTtl;
    private final Duration processingTtl;
    private final int maxEntries;

    private final ScheduledExecutorService cleaner;
    private final String keyPrefix; // normalized, may be ""

    /**
     * Backward compatible constructor (no prefix).
     */
    public InMemoryIdempotencyStore(Duration doneTtl, Duration processingTtl, int maxEntries, Duration cleanupInterval) {
        this("", doneTtl, processingTtl, maxEntries, cleanupInterval);
    }

    /**
     * New constructor: allow key namespacing (recommended: include groupId in prefix).
     * Example: "lsf:dedup:it-group:".
     */
    public InMemoryIdempotencyStore(
            String keyPrefix,
            Duration doneTtl,
            Duration processingTtl,
            int maxEntries,
            Duration cleanupInterval
    ) {
        this.doneTtl = requirePositive(doneTtl, "doneTtl");
        this.processingTtl = requirePositive(processingTtl, "processingTtl");
        this.maxEntries = Math.max(1000, maxEntries);
        this.keyPrefix = normalizePrefix(keyPrefix);

        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lsf-idempotency-cleaner");
            t.setDaemon(true);
            return t;
        });

        long periodMs = Math.max(1_000L, cleanupInterval.toMillis());
        cleaner.scheduleAtFixedRate(this::cleanupExpiredSafe, periodMs, periodMs, TimeUnit.MILLISECONDS);
    }

    private String key(String eventId) {
        return keyPrefix + eventId;
    }

    @Override
    public boolean isProcessed(String eventId) {
        String k = key(eventId);
        Entry e = entries.get(k);
        if (e == null) return false;

        long now = System.currentTimeMillis();
        if (now > e.expireAtMs()) {
            entries.remove(k, e);
            return false;
        }
        return e.state() == State.DONE;
    }

    @Override
    public void markProcessed(String eventId) {
        String k = key(eventId);
        long exp = System.currentTimeMillis() + doneTtl.toMillis();
        entries.put(k, new Entry(State.DONE, exp, null));

        if (entries.size() > maxEntries) {
            cleanupExpired();
            trimToMaxEntries();
        }
    }

    @Override
    public boolean tryMarkProcessed(String eventId) {
        String k = key(eventId);
        long now = System.currentTimeMillis();
        long exp = now + doneTtl.toMillis();

        while (true) {
            Entry cur = entries.get(k);
            if (cur == null) {
                Entry prev = entries.putIfAbsent(k, new Entry(State.DONE, exp, null));
                if (prev == null) {
                    if (entries.size() > maxEntries) {
                        cleanupExpired();
                        trimToMaxEntries();
                    }
                    return true;
                }
                continue;
            }

            if (now > cur.expireAtMs()) {
                entries.remove(k, cur);
                continue;
            }
            return false;
        }
    }

    @Override
    public void unmarkProcessed(String eventId) {
        entries.remove(key(eventId));
    }

    @Override
    public Lease tryBeginProcessing(String eventId) {
        String k = key(eventId);
        long now = System.currentTimeMillis();
        final String token = java.util.UUID.randomUUID().toString();
        final long processingExp = now + processingTtl.toMillis();

        while (true) {
            Entry cur = entries.get(k);
            if (cur == null) {
                Entry prev = entries.putIfAbsent(k, new Entry(State.PROCESSING, processingExp, token));
                if (prev == null) {
                    if (entries.size() > maxEntries) {
                        cleanupExpired();
                        trimToMaxEntries();
                    }
                    return Lease.acquired(token);
                }
                continue;
            }

            if (now > cur.expireAtMs()) {
                entries.remove(k, cur);
                continue;
            }

            if (cur.state() == State.DONE) return Lease.duplicate();
            return Lease.inFlight();
        }
    }

    @Override
    public void markDone(String eventId, String token) {
        String k = key(eventId);
        long now = System.currentTimeMillis();
        long doneExp = now + doneTtl.toMillis();

        entries.computeIfPresent(k, (kk, cur) -> {
            if (cur.state() == State.PROCESSING && Objects.equals(cur.token(), token)) {
                return new Entry(State.DONE, doneExp, null);
            }
            return cur;
        });
    }

    @Override
    public void releaseProcessing(String eventId, String token) {
        String k = key(eventId);
        entries.computeIfPresent(k, (kk, cur) -> {
            if (cur.state() == State.PROCESSING && Objects.equals(cur.token(), token)) {
                return null; // remove
            }
            return cur;
        });
    }

    private void cleanupExpiredSafe() {
        try {
            cleanupExpired();
        } catch (Exception e) {
            log.warn("Idempotency cleanup failed", e);
        }
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Entry> e = it.next();
            if (now > e.getValue().expireAtMs()) {
                it.remove();
            }
        }
    }

    private void trimToMaxEntries() {
        int over = entries.size() - maxEntries;
        if (over <= 0) return;

        Iterator<String> it = entries.keySet().iterator();
        int removed = 0;
        while (it.hasNext() && removed < over) {
            it.next();
            it.remove();
            removed++;
        }
    }

    @Override
    public void close() {
        cleaner.shutdownNow();
    }

    private static Duration requirePositive(Duration d, String name) {
        if (d == null || d.isZero() || d.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return d;
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) return "";
        String p = prefix.trim();
        return p.endsWith(":") ? p : (p + ":");
    }
}
