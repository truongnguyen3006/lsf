package com.myorg.lsf.kafka;

public final class LsfDlqHeaders {
    private LsfDlqHeaders() {}

    public static final String REASON = "lsf.dlq.reason";
    public static final String NON_RETRYABLE = "lsf.dlq.non_retryable";

    public static final String EXCEPTION_CLASS = "lsf.dlq.exception_class";
    public static final String EXCEPTION_MESSAGE = "lsf.dlq.exception_message";

    public static final String SERVICE = "lsf.dlq.service";
    public static final String TS_MS = "lsf.dlq.ts_ms";
}
