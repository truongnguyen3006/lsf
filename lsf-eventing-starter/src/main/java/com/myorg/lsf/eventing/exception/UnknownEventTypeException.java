package com.myorg.lsf.eventing.exception;

import com.myorg.lsf.contracts.core.exception.LsfNonRetryableException;

public class UnknownEventTypeException extends LsfNonRetryableException {
    public UnknownEventTypeException(String eventType, String eventId) {
        super("UNKNOWN_EVENT_TYPE", "No handler for eventType=" + eventType + ", eventId=" + eventId);
    }
}
