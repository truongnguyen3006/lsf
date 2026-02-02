package com.demo.app;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.eventing.LsfEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DemoHandlers {
    private final Environment springEnv;

    @LsfEventHandler(value = TestController.DEMO_EVENT_TYPE, payload = HelloPayload.class)
    public void onHello(EventEnvelope envelope, HelloPayload payload) {
        String port = springEnv.getProperty("server.port");
        String eventId = envelope.getEventId();

        // Demo: force failures to observe retry + DLQ.
        // - eventId starts with "FAIL_" => always throw (will retry then go to demo-topic.DLQ)
        if (eventId != null && eventId.startsWith("FAIL_")) {
            log.warn("FORCED FAIL on port={} eventId={} hello={}", port, eventId, payload.getHello());
            throw new RuntimeException("Simulated handler failure for eventId=" + eventId);
        }

        log.info("HANDLED on port={} eventId={} hello={}", port, eventId, payload.getHello());
    }
}
