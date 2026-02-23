package com.myorg.lsf.outbox.mysql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;

@RequiredArgsConstructor
public class JdbcOutboxWriter implements OutboxWriter {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final LsfOutboxMySqlProperties props;

    @Override
    public long append(EventEnvelope envelope, String topic, String key) {
        if (envelope == null) throw new IllegalArgumentException("envelope must not be null");
        if (topic == null || topic.isBlank()) throw new IllegalArgumentException("topic must not be blank");

        String envelopeJson = toJson(envelope);

        String sql = "INSERT INTO " + props.getTable() +
                " (topic, msg_key, event_id, event_type, correlation_id, aggregate_id, envelope_json)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?)";

        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            // ask only for the 'id' column key (fixes H2 returning multiple keys)
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, topic);
            ps.setString(2, key);
            ps.setString(3, envelope.getEventId());
            ps.setString(4, envelope.getEventType());
            ps.setString(5, envelope.getCorrelationId());
            ps.setString(6, envelope.getAggregateId());
            ps.setString(7, envelopeJson);
            return ps;
        }, kh);

        Number k = kh.getKey();
        if (k != null) return k.longValue();

        var keys = kh.getKeys();
        if (keys != null) {
            Object id = keys.get("id");
            if (id == null) id = keys.get("ID");
            if (id instanceof Number n) return n.longValue();
        }

        throw new IllegalStateException("No generated key 'id' returned from insert into " + props.getTable());
    }


    private String toJson(EventEnvelope env) {
        try {
            return mapper.writeValueAsString(env);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize EventEnvelope to JSON", e);
        }
    }
}
