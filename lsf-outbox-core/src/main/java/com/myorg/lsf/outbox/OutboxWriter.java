package com.myorg.lsf.outbox;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;

public interface OutboxWriter {

    long append(EventEnvelope envelope, String topic, String key);
}
