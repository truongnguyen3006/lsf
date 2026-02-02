package com.myorg.lsf.contracts.core.exception;

public class LsfNonRetryableException extends RuntimeException {
    public LsfNonRetryableException(String message) { super(message); }
}
