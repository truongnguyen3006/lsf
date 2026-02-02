package com.myorg.lsf.contracts.core.envelope;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventEnvelope {
    private String eventId; // UUID
    private String eventType;  // e.g. "ecommerce.order.placed.v1"
    private int version;  // 1

    private String aggregateId; // workflowId/orderNumber/bookingId...
    private String correlationId;   // trace across services
    private String causationId;  // parent event id (optional)

    private long occurredAtMs; // epoch millis
    private String producer; // service name (optional)

    private JsonNode payload; // actual payload
    private ErrorInfo error; //optional
}
