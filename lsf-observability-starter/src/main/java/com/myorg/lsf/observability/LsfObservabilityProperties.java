package com.myorg.lsf.observability;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lsf.observability")
public class LsfObservabilityProperties {
    private boolean enabled = true;

    private boolean mdcEnabled = true;
    private boolean metricsEnabled = true;
    private boolean tracingEnabled = true;

    // Tag an toàn cardinality
    private boolean tagTopic = true;
    private boolean tagEventType = true;
    private boolean tagOutcome = true;

    // KHÔNG tag eventId (để tránh cardinality nổ)
    private boolean tagEventId = false;

    // Header names (chuẩn hoá)
    private String headerEventId = "lsf-event-id";
    private String headerEventType = "lsf-event-type";
    private String headerCorrelationId = "lsf-correlation-id";
    private String headerCausationId = "lsf-causation-id";
    private String headerProducer = "lsf-producer";

    // timeout mặc định cho span/timer nếu cần
}
