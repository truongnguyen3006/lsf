package com.myorg.lsf.outbox.postgres;

public record OutboxRow(
        long id,
        String topic,
        String msgKey,
        String eventId,
        String envelopeJson,
        int retryCount
) {}