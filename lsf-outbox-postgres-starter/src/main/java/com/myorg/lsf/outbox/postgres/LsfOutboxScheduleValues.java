package com.myorg.lsf.outbox.postgres;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LsfOutboxScheduleValues {
    private final LsfOutboxPostgresProperties props;

    public long getPollIntervalMs() { return props.getPublisher().getPollInterval().toMillis(); }
    public long getInitialDelayMs() { return props.getPublisher().getInitialDelay().toMillis(); }
}