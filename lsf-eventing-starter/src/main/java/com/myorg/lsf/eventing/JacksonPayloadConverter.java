package com.myorg.lsf.eventing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.KafkaNull;

import java.util.Map;

public class JacksonPayloadConverter implements PayloadConverter {

    private final ObjectMapper mapper;

    public JacksonPayloadConverter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public EventEnvelope toEnvelope(Object value) {
        if (value == null || value == KafkaNull.INSTANCE) return null;

        if (value instanceof EventEnvelope env) return env;
        if (value instanceof ConsumerRecord<?, ?> rec) return toEnvelope(rec.value());

        try {
            if (value instanceof JsonNode node) {
                return mapper.treeToValue(node, EventEnvelope.class);
            }
            if (value instanceof Map<?, ?> map) {
                return mapper.convertValue(map, EventEnvelope.class);
            }
            if (value instanceof String s) {
                return mapper.readValue(s, EventEnvelope.class);
            }

            // fallback: cố convert mọi kiểu object khác
            return mapper.convertValue(value, EventEnvelope.class);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Cannot convert Kafka value to EventEnvelope. valueType=" + value.getClass(), e
            );
        }
    }
}
