package com.demo.app;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DlqListener {
    @KafkaListener(topics = "demo-topic.DLQ", groupId = "lsf-example-dlq",
            containerFactory = "kafkaListenerContainerFactory")
    public void onDlq(EventEnvelope env) {
        System.out.println("DLQ RECEIVED: eventType=" + env.getEventType()
                + ", eventId=" + env.getEventId());
    }
}
