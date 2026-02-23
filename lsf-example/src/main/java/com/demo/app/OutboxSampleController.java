package com.demo.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lsf.outbox", name = "enabled", havingValue = "true")
public class OutboxSampleController {

    private final OutboxWriter outboxWriter;
    private final ObjectMapper mapper;

    @PostMapping("/outbox/append")
    @Transactional
    public String append(
            @RequestParam(name = "eventId", defaultValue = "E_OUTBOX_1") String eventId,
            @RequestParam(name = "topic", defaultValue = "demo-topic") String topic,
            @RequestParam(name = "key", defaultValue = "k1") String key
    ) {
        EventEnvelope env = EventEnvelope.builder()
                .eventId(eventId)
                .eventType(TestController.DEMO_EVENT_TYPE)
                .version(1)
                .aggregateId("agg-1")
                .correlationId("corr-1")
                .occurredAtMs(System.currentTimeMillis())
                .producer("lsf-example")
                .payload(mapper.valueToTree(HelloPayload.builder().hello("from-outbox").build()))
                .build();

        long id = outboxWriter.append(env, topic, key); // ✅ one-liner API
        return "outboxed id=" + id + " eventId=" + eventId;
    }
}
