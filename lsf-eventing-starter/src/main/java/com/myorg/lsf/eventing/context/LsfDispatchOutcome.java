package com.myorg.lsf.eventing.context;

/**
 * Thread-local marker used by inner dispatcher wrappers (idempotency) to communicate
 * non-standard outcomes (duplicate / in-flight) to outer layers (observability).
 */
public final class LsfDispatchOutcome {

    public static final String DUPLICATE = "duplicate";
    public static final String IN_FLIGHT = "in_flight";

    private static final ThreadLocal<String> OUTCOME = new ThreadLocal<>();

    private LsfDispatchOutcome() {}

    public static void markDuplicate() {
        OUTCOME.set(DUPLICATE);
    }

    public static void markInFlight() {
        OUTCOME.set(IN_FLIGHT);
    }

    /** Read and clear. */
    public static String consume() {
        String v = OUTCOME.get();
        OUTCOME.remove();
        return v;
    }

    /** Clear without reading (safety net). */
    public static void clear() {
        OUTCOME.remove();
    }
}
