package com.myorg.lsf.eventing.idempotency;

//Hợp đồng định nghĩa cách thức lưu trữ trạng thái xử lý của một eventId
public interface IdempotencyStore extends AutoCloseable {

    enum Decision {
        ACQUIRED,
        DUPLICATE,
        IN_FLIGHT
    }

    record Lease(Decision decision, String token) {
        public static Lease acquired(String token) { return new Lease(Decision.ACQUIRED, token); }
        public static Lease duplicate() { return new Lease(Decision.DUPLICATE, null); }
        public static Lease inFlight() { return new Lease(Decision.IN_FLIGHT, null); }
    }

    // --- Legacy API (kept to avoid breaking other modules in this repo) ---
    default boolean isProcessed(String eventId) {
        throw new UnsupportedOperationException("isProcessed not implemented");
    }

    default void markProcessed(String eventId) {
        throw new UnsupportedOperationException("markProcessed not implemented");
    }

    /**
     * Atomically mark the eventId as processed if it was not previously marked.
     */
    default boolean tryMarkProcessed(String eventId) {
        // Best-effort fallback using legacy methods (NOT atomic). Concrete stores must override.
        if (isProcessed(eventId)) return false;
        markProcessed(eventId);
        return true;
    }

    // --- New API---

    default Lease tryBeginProcessing(String eventId) {
        // Fallback to legacy behaviour: mark immediately.
        return tryMarkProcessed(eventId) ? Lease.acquired(null) : Lease.duplicate();
    }

    default void markDone(String eventId, String token) {
        // Legacy stores already mark on acquire.
    }

    default void releaseProcessing(String eventId, String token) {
        unmarkProcessed(eventId);
    }

    default void unmarkProcessed(String eventId) {
        // no-op by default
    }

    @Override
    default void close() {
        // no-op by default
    }
}
