package com.myorg.lsf.kafka;

public enum LsfDlqReason {
    RETRY_EXHAUSTED("RETRY_EXHAUSTED"),
    DESERIALIZATION("DESERIALIZATION"),
    NON_RETRYABLE("NON_RETRYABLE"),
    UNKNOWN("UNKNOWN");

    private final String code;

    LsfDlqReason(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
