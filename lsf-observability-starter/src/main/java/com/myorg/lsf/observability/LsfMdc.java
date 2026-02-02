package com.myorg.lsf.observability;
import org.slf4j.MDC;
public final class LsfMdc {
    public static void put(LsfContext c) {
        if (c == null) return;
        if (c.eventId() != null) MDC.put("eventId", c.eventId());
        if (c.eventType() != null) MDC.put("eventType", c.eventType());
        if (c.correlationId() != null) MDC.put("corrId", c.correlationId());
        if (c.producer() != null) MDC.put("producer", c.producer());
        if (c.topic() != null) MDC.put("topic", c.topic());
        if (c.partition() != null) MDC.put("partition", String.valueOf(c.partition()));
        if (c.offset() != null) MDC.put("offset", String.valueOf(c.offset()));
    }

    public static void clear() {
        MDC.remove("eventId");
        MDC.remove("eventType");
        MDC.remove("corrId");
        MDC.remove("producer");
        MDC.remove("topic");
        MDC.remove("partition");
        MDC.remove("offset");
    }
}
