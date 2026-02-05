-- Outbox table (MySQL/InnoDB)
-- Stores the entire EventEnvelope as JSON alongside indexed fields.

CREATE TABLE IF NOT EXISTS lsf_outbox (
                                          id BIGINT NOT NULL AUTO_INCREMENT,

                                          topic VARCHAR(255) NOT NULL,
    msg_key VARCHAR(255) NULL,

    event_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    correlation_id VARCHAR(100) NULL,
    aggregate_id VARCHAR(100) NULL,

    envelope_json LONGTEXT NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP NULL,
    retry_count INT NOT NULL DEFAULT 0,
    last_error TEXT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_lsf_outbox_event_id (event_id),
    KEY idx_lsf_outbox_status_created (status, created_at)
    ) ENGINE=InnoDB;
