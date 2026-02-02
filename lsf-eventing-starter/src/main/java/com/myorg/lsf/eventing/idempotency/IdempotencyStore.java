package com.myorg.lsf.eventing.idempotency;

/**
 * Idempotency store used by {@code IdempotentLsfDispatcher}.
 *
 * <p>Important: for correct cross-instance deduplication, the store should
 * implement {@link #tryMarkProcessed(String)} atomically.
 */
public interface IdempotencyStore extends AutoCloseable {

    /**
     * Result of an idempotency acquire attempt.
     */
    enum Decision {
        /** This caller acquired the right to process this eventId now. */
        ACQUIRED,
        /** This eventId was already completed before (duplicate). */
        DUPLICATE,
        /** Another consumer instance is currently processing this eventId. */
        IN_FLIGHT
    }

    /**
     * A short-lived lease for processing an eventId.
     * <p>
     * Stores that support cross-instance locking should return a non-empty token
     * and verify it in {@link #markDone(String, String)} / {@link #releaseProcessing(String, String)}.
     */
    record Lease(Decision decision, String token) {
        public static Lease acquired(String token) { return new Lease(Decision.ACQUIRED, token); }
        public static Lease duplicate() { return new Lease(Decision.DUPLICATE, null); }
        public static Lease inFlight() { return new Lease(Decision.IN_FLIGHT, null); }
    }

    // --- Legacy API (kept to avoid breaking other modules in this repo) ---
    default boolean isProcessed(String eventId) {
        // Fallback implementation; concrete stores should override tryMarkProcessed.
        throw new UnsupportedOperationException("isProcessed not implemented");
    }

    default void markProcessed(String eventId) {
        throw new UnsupportedOperationException("markProcessed not implemented");
    }

    // --- Preferred API ---

    /**
     * Atomically mark the eventId as processed if it was not previously marked.
     *
     * @return {@code true} if this call marked the eventId for the first time,
     *         {@code false} if the eventId was already processed (duplicate).
     */
    default boolean tryMarkProcessed(String eventId) {
        // Best-effort fallback using legacy methods (NOT atomic). Concrete stores must override.
        if (isProcessed(eventId)) return false;
        markProcessed(eventId);
        return true;
    }

    // --- New API (recommended) ---

    /**
     * Acquire a short-lived processing lease for this eventId.
     * <p>
     * Semantics:
     * <ul>
     *   <li>If the eventId is already {@code DONE} → return {@link Lease#duplicate()}.</li>
     *   <li>If another instance is processing (lease exists) → return {@link Lease#inFlight()}.</li>
     *   <li>If acquired successfully → return {@link Lease#acquired(String)}.</li>
     * </ul>
     */
    default Lease tryBeginProcessing(String eventId) {
        // Fallback to legacy behaviour: mark immediately.
        return tryMarkProcessed(eventId) ? Lease.acquired(null) : Lease.duplicate();
    }

    /**
     * Mark the eventId as {@code DONE} after successful handling.
     * Implementations should be best-effort and ideally verify the token.
     */
    default void markDone(String eventId, String token) {
        // Legacy stores already mark on acquire.
    }

    /**
     * Release a previously acquired processing lease (best-effort), so the message can be retried.
     * Implementations should verify token where supported.
     */
    default void releaseProcessing(String eventId, String token) {
        // Best-effort fallback
        unmarkProcessed(eventId);
    }

    /**
     * Undo a previous successful {@link #tryMarkProcessed(String)} (best-effort).
     * Used when handler fails so the message can be retried.
     */
    default void unmarkProcessed(String eventId) {
        // no-op by default
    }

    @Override
    default void close() {
        // no-op by default
    }
}
