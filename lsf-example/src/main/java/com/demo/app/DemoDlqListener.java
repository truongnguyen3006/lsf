package com.demo.app;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Demo DLQ consumer so you can see when a message ends up in the DLQ just by looking at logs.
 */
@Slf4j
@Component
public class DemoDlqListener {

    @KafkaListener(
            topics = "demo-topic.DLQ",
            groupId = "${spring.application.name:app}-dlq-${server.port}"
    )
    public void onDlq(
            EventEnvelope envelope,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_TOPIC, required = false) String origTopic,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_PARTITION, required = false) Integer origPartition,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_OFFSET, required = false) Long origOffset,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_FQCN, required = false) String exClass,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String exMsg
    ) {
        log.error(
                "DLQ RECEIVED topic={} partition={} offset={} origTopic={} origPartition={} origOffset={} eventId={} eventType={} exClass={} exMsg={}",
                topic, partition, offset,
                origTopic, origPartition, origOffset,
                envelope.getEventId(), envelope.getEventType(),
                exClass, exMsg
        );
    }
}
