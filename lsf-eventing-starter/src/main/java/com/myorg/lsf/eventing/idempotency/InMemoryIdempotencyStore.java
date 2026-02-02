package com.myorg.lsf.eventing.idempotency;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class InMemoryIdempotencyStore implements IdempotencyStore, AutoCloseable  {
    private enum State { PROCESSING, DONE }

    private record Entry(State state, long expireAtMs, String token) {}

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    private final Duration doneTtl;
    private final Duration processingTtl;
    private final int maxEntries;

    private final ScheduledExecutorService cleaner;

    public InMemoryIdempotencyStore(Duration doneTtl, Duration processingTtl, int maxEntries, Duration cleanupInterval) {
        this.doneTtl = doneTtl;
        this.processingTtl = processingTtl;
        this.maxEntries = maxEntries;

        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lsf-idempotency-cleaner");
            t.setDaemon(true);
            return t;
        });

        long periodMs = Math.max(1_000L, cleanupInterval.toMillis());
        cleaner.scheduleAtFixedRate(this::cleanupExpiredSafe, periodMs, periodMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean isProcessed(String eventId) {
        Entry e = entries.get(eventId);
        if (e == null) return false;

        long now = System.currentTimeMillis();
        if (now > e.expireAtMs()) {
            entries.remove(eventId, e);
            return false;
        }
        return e.state() == State.DONE;
    }

    @Override
    public void markProcessed(String eventId) {
        long exp = System.currentTimeMillis() + doneTtl.toMillis();
        entries.put(eventId, new Entry(State.DONE, exp, null));

        // tránh memory phình: nếu vượt ngưỡng thì dọn nhanh
        if (entries.size() > maxEntries) {
            cleanupExpired();
            // nếu vẫn quá lớn -> xoá bớt vài key “ngẫu nhiên” (best-effort)
            trimToMaxEntries();
        }
    }

    @Override
    public boolean tryMarkProcessed(String eventId) {
        long now = System.currentTimeMillis();
        long exp = now + doneTtl.toMillis();

        // Loop to handle the case where an existing key is expired.
        while (true) {
            Entry cur = entries.get(eventId);
            if (cur == null) {
                Entry prev = entries.putIfAbsent(eventId, new Entry(State.DONE, exp, null));
                if (prev == null) {
                    // newly marked
                    if (entries.size() > maxEntries) {
                        cleanupExpired();
                        trimToMaxEntries();
                    }
                    return true;
                }
                continue;
            }

            if (now > cur.expireAtMs()) {
                // expired -> remove and retry
                entries.remove(eventId, cur);
                continue;
            }
            return false; // already exists (DONE or PROCESSING)
        }
    }

    @Override
    public void unmarkProcessed(String eventId) {
        entries.remove(eventId);
    }

    @Override
    public Lease tryBeginProcessing(String eventId) {
        long now = System.currentTimeMillis();
        final String token = java.util.UUID.randomUUID().toString();
        final long processingExp = now + processingTtl.toMillis();

        while (true) {
            Entry cur = entries.get(eventId);
            if (cur == null) {
                Entry prev = entries.putIfAbsent(eventId, new Entry(State.PROCESSING, processingExp, token));
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
                entries.remove(eventId, cur);
                continue;
            }

            if (cur.state() == State.DONE) return Lease.duplicate();
            return Lease.inFlight();
        }
    }

    @Override
    public void markDone(String eventId, String token) {
        long now = System.currentTimeMillis();
        long doneExp = now + doneTtl.toMillis();
        entries.computeIfPresent(eventId, (k, cur) -> {
            if (cur.state() == State.PROCESSING && java.util.Objects.equals(cur.token(), token)) {
                return new Entry(State.DONE, doneExp, null);
            }
            return cur;
        });
    }

    @Override
    public void releaseProcessing(String eventId, String token) {
        entries.computeIfPresent(eventId, (k, cur) -> {
            if (cur.state() == State.PROCESSING && java.util.Objects.equals(cur.token(), token)) {
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

}
