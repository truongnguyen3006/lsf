package com.myorg.lsf.outbox.mysql;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LsfOutboxScheduleValues {
    private final LsfOutboxMySqlProperties props;

    public long getPollIntervalMs() {
        return props.getPublisher().getPollInterval().toMillis();
    }

    public long getInitialDelayMs() {
        return props.getPublisher().getInitialDelay().toMillis();
    }
}
