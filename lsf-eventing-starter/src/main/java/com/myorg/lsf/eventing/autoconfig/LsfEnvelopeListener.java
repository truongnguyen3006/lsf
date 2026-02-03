package com.myorg.lsf.eventing.autoconfig;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.eventing.LsfDispatcher;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.springframework.kafka.annotation.KafkaListener;

import java.util.List;

@RequiredArgsConstructor
public class LsfEnvelopeListener {

    private final LsfDispatcher dispatcher;

    @KafkaListener(
            topics = "${lsf.eventing.consume-topics:demo-topic}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessage(Object payload) {
        if (payload == null) return;

        // single value
        if (payload instanceof EventEnvelope env) {
            dispatcher.dispatch(env);
            return;
        }

        // single record
        if (payload instanceof ConsumerRecord<?, ?> rec) {
            handleValue(rec.value());
            return;
        }

        // batch records (ConsumerRecords)
        if (payload instanceof ConsumerRecords<?, ?> recs) {
            recs.forEach(r -> handleValue(r.value()));
            return;
        }

        // batch as List (values or records)
        if (payload instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof EventEnvelope env) {
                    dispatcher.dispatch(env);
                } else if (item instanceof ConsumerRecord<?, ?> rec) {
                    handleValue(rec.value());
                } else {
                    handleValue(item);
                }
            }
            return;
        }

        throw new IllegalArgumentException("Unsupported Kafka payload type: " + payload.getClass());
    }

    private void handleValue(Object value) {
        if (value == null) return;
        if (value instanceof EventEnvelope env) {
            dispatcher.dispatch(env);
            return;
        }
        throw new IllegalArgumentException("Expected EventEnvelope but got: " + value.getClass());
    }
}
