package com.myorg.lsf.eventing.exception;

public class LsfRetryableException extends RuntimeException {
    public LsfRetryableException(String msg) { super(msg); }
    public LsfRetryableException(String msg, Throwable cause) { super(msg, cause); }
}
