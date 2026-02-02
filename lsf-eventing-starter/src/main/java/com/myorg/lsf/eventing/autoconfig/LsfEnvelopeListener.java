package com.myorg.lsf.eventing.autoconfig;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.eventing.LsfDispatcher;
import com.myorg.lsf.eventing.LsfEventingProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;

//để app không phải tự viết @KafkaListener
@Data
@AllArgsConstructor
public class LsfEnvelopeListener {

    private final LsfDispatcher dispatcher;
    private final LsfEventingProperties props;

    @KafkaListener(
            topics = "#{@lsfConsumeTopics}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void on(
            EventEnvelope env,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        // set MDC (nếu bạn muốn đặt ở đây thay vì dispatcher)
        org.slf4j.MDC.put("topic", topic);
        org.slf4j.MDC.put("partition", String.valueOf(partition));
        org.slf4j.MDC.put("offset", String.valueOf(offset));

        try {
            dispatcher.dispatch(env);
        } finally {
            org.slf4j.MDC.remove("topic");
            org.slf4j.MDC.remove("partition");
            org.slf4j.MDC.remove("offset");
        }
    }
}
