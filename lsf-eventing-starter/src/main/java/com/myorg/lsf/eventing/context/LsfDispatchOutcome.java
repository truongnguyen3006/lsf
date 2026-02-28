package com.myorg.lsf.eventing.context;
//Dùng ThreadLocal để truyền thông tin trạng thái xử lý (DUPLICATE, IN_FLIGHT)
// từ bên trong lõi ra ngoài lớp bao bọc
// mà không làm thay đổi chữ ký hàm (method signature).
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
