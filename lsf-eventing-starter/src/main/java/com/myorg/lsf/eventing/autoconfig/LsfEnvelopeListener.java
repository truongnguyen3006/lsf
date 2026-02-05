package com.myorg.lsf.eventing.autoconfig;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.eventing.PayloadConverter;
import com.myorg.lsf.eventing.LsfDispatcher;
import com.myorg.lsf.eventing.context.LsfDispatchOutcome;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.springframework.kafka.annotation.KafkaListener;

import java.util.List;

@RequiredArgsConstructor
public class LsfEnvelopeListener {

    private final LsfDispatcher dispatcher;
    private final PayloadConverter payloadConverter;

    @KafkaListener(
            topics = "#{@lsfConsumeTopics}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessage(Object payload) {
        if (payload == null) return;

        try {
            // single record
            if (payload instanceof ConsumerRecord<?, ?> rec) {
                dispatchOne(rec.value());
                return;
            }

            // batch records (ConsumerRecords)
            if (payload instanceof ConsumerRecords<?, ?> recs) {
                recs.forEach(r -> dispatchOne(r.value()));
                return;
            }

            // batch as List (values or records)
            if (payload instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof ConsumerRecord<?, ?> rec2) {
                        dispatchOne(rec2.value());
                    } else {
                        dispatchOne(item);
                    }
                }
                return;
            }

            // single value
            dispatchOne(payload);
        } finally {
            // safety net: avoid leaking marker between polls/records
            LsfDispatchOutcome.clear();
        }
    }

    private void dispatchOne(Object value) {
        if (value == null) return;

        try {
            EventEnvelope env = payloadConverter.toEnvelope(value);
            if (env == null) return;
            dispatcher.dispatch(env);
        } finally {
            // if observability wrapper isn't present, this prevents thread-local leak
            LsfDispatchOutcome.clear();
        }
    }
}
