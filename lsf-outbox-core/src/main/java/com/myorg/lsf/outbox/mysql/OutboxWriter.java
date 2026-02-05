package com.myorg.lsf.outbox.mysql;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;

/**
 * Append events to the Outbox table within the caller's DB transaction.
 *
 * <p>Usage (inside @Transactional):
 * <pre>
 *   outboxWriter.append(envelope, "demo-topic", "key-1");
 * </pre>
 */
public interface OutboxWriter {

    /**
     * Insert one row into outbox.
     *
     * @return generated outbox id
     */
    long append(EventEnvelope envelope, String topic, String key);
}
