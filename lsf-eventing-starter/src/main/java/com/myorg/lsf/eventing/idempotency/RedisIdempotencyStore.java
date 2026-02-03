package com.myorg.lsf.eventing.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

@RequiredArgsConstructor
public class RedisIdempotencyStore implements IdempotencyStore {

    private final StringRedisTemplate redis;
    private final Duration doneTtl;
    private final Duration processingTtl;
    private final String keyPrefix;

    private static final String PROCESSING_PREFIX = "P:";
    private static final String DONE_VALUE = "D";

    // 0=acquired, 1=duplicate(done), 2=inFlight(processing)
    private static final DefaultRedisScript<Long> TRY_BEGIN_SCRIPT = new DefaultRedisScript<>(
            "local ok = redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2]); " +
                    "if ok then return 0 end " +
                    "local v = redis.call('GET', KEYS[1]); " +
                    "if v == ARGV[3] then return 1 end " +
                    "return 2",
            Long.class
    );

    private static final DefaultRedisScript<Long> MARK_DONE_SCRIPT = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]); " +
                    "if v == ARGV[1] then " +
                    "  redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3]); " +
                    "  return 1 " +
                    "end " +
                    "return 0",
            Long.class
    );

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]); " +
                    "if v == ARGV[1] then return redis.call('DEL', KEYS[1]) end " +
                    "return 0",
            Long.class
    );

    private String key(String eventId) {
        // normalizePrefix để tránh dính key
        return normalizePrefix(keyPrefix) + eventId;
    }

    @Override
    public boolean isProcessed(String eventId) {
        String v = redis.opsForValue().get(key(eventId));
        return DONE_VALUE.equals(v);
    }

    @Override
    public void markProcessed(String eventId) {
        requirePositive(doneTtl, "doneTtl");
        redis.opsForValue().set(key(eventId), DONE_VALUE, doneTtl);
    }

    @Override
    public boolean tryMarkProcessed(String eventId) {
        requirePositive(doneTtl, "doneTtl");
        Boolean ok = redis.opsForValue().setIfAbsent(key(eventId), DONE_VALUE, doneTtl);
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public void unmarkProcessed(String eventId) {
        redis.delete(key(eventId));
    }

    @Override
    public Lease tryBeginProcessing(String eventId) {
        requirePositive(processingTtl, "processingTtl");

        String k = key(eventId);
        String token = java.util.UUID.randomUUID().toString();
        String processingValue = PROCESSING_PREFIX + token;

        Long res = redis.execute(
                TRY_BEGIN_SCRIPT,
                List.of(k),
                processingValue,
                String.valueOf(processingTtl.toMillis()),
                DONE_VALUE
        );

        long r = res == null ? 2L : res;
        if (r == 0L) return Lease.acquired(token);
        if (r == 1L) return Lease.duplicate();
        return Lease.inFlight();
    }

    @Override
    public void markDone(String eventId, String token) {
        requirePositive(doneTtl, "doneTtl");

        String k = key(eventId);
        String processingValue = PROCESSING_PREFIX + token;

        redis.execute(
                MARK_DONE_SCRIPT,
                List.of(k),
                processingValue,
                DONE_VALUE,
                String.valueOf(doneTtl.toMillis())
        );
    }

    @Override
    public void releaseProcessing(String eventId, String token) {
        String k = key(eventId);
        String processingValue = PROCESSING_PREFIX + token;

        redis.execute(
                RELEASE_SCRIPT,
                List.of(k),
                processingValue
        );
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
