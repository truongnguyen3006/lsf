package com.myorg.lsf.observability;

public record LsfContext(
        String eventId,
        String eventType,
        String correlationId,
        String causationId,
        String producer,
        String topic,
        Integer partition,
        Long offset
) {}
