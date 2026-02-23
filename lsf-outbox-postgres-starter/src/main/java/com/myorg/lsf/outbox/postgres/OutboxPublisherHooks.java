package com.myorg.lsf.outbox.postgres;

import java.util.List;

public interface OutboxPublisherHooks {
    default void afterClaim(List<OutboxRow> claimedRows) {}
    default void beforeSend(OutboxRow row) {}
}