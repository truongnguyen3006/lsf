package com.myorg.lsf.outbox.mysql;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class JdbcOutboxRepository {
    private final JdbcTemplate jdbc;
    private final LsfOutboxMySqlProperties props;
    private String t(){
        return props.getTable();
    }

    public int claimBatch(String owner, Instant now, Instant leaseUntil, int limit){
        String sql = """
                UPDATE %s
                SET status='PROCESSING', lease_owner=?, lease_until=?
                WHERE id IN (
                    SELECT id FROM (
                        SELECT id
                        FROM %s
                        WHERE (
                            status='NEW'
                            OR (status='RETRY' AND (next_attempt_at IS NULL OR next_attempt_at <= ?))
                            OR (status='PROCESSING' AND lease_until IS NOT NULL AND lease_until < ?)
                        )
                        ORDER BY id
                        LIMIT ?
                    ) x
                )
                """.formatted(t(), t());

        return jdbc.update(sql,
                owner,
                Timestamp.from(leaseUntil),
                Timestamp.from(now),
                Timestamp.from(now),
                limit
        );
    }

    public List<OutboxRow> findClaimed(String owner, Instant now, int limit) {
        String sql = """
            SELECT id, topic, msg_key, event_id, envelope_json, retry_count
            FROM %s
            WHERE status='PROCESSING'
              AND lease_owner=?
              AND lease_until IS NOT NULL
              AND lease_until >= ?
            ORDER BY id
            LIMIT ?
            """.formatted(t());

        return jdbc.query(sql,
                (rs, i) -> new OutboxRow(
                        rs.getLong("id"),
                        rs.getString("topic"),
                        rs.getString("msg_key"),
                        rs.getString("event_id"),
                        rs.getString("envelope_json"),
                        rs.getInt("retry_count")
                ),
                owner,
                Timestamp.from(now),
                limit
        );
    }

    public void markSent(long id, Instant sentAt) {
        String sql = """
                UPDATE %s
                SET status='SENT', sent_at=?, lease_owner=NULL, lease_until=NULL, next_attempt_at=NULL
                WHERE id=?
                """.formatted(t());
        jdbc.update(sql, Timestamp.from(sentAt), id);
    }

    public void markRetry(long id, Instant nextAttemptAt, String lastError) {
        String sql = """
                UPDATE %s
                SET status='RETRY',
                    retry_count = retry_count + 1,
                    next_attempt_at = ?,
                    last_error = ?,
                    lease_owner=NULL,
                    lease_until=NULL
                WHERE id=?
                """.formatted(t());
        jdbc.update(sql, Timestamp.from(nextAttemptAt), lastError, id);
    }

    public void markFailed(long id, String lastError) {
        String sql = """
                UPDATE %s
                SET status='FAILED',
                    last_error = ?,
                    lease_owner=NULL,
                    lease_until=NULL
                WHERE id=?
                """.formatted(t());
        jdbc.update(sql, lastError, id);
    }

    public String statusByEventId(String eventId) {
        String sql = "SELECT status FROM " + t() + " WHERE event_id = ?";
        return jdbc.queryForObject(sql, String.class, eventId);
    }

    public int countPending(Instant now) {
        String sql = """
                SELECT COUNT(*)
                FROM %s
                WHERE (
                    status='NEW'
                    OR (status='RETRY' AND (next_attempt_at IS NULL OR next_attempt_at <= ?))
                )
                """.formatted(t());
        Integer v = jdbc.queryForObject(sql, Integer.class, Timestamp.from(now));
        return v == null ? 0 : v;
    }

    public int claimBatchSkipLocked(String owner, Instant now, Instant leaseUntil, int limit) {
        // Must be called inside a DB transaction (TransactionTemplate) so row locks are held.
        String selectSql = """
            SELECT id
            FROM %s
            WHERE (
                status='NEW'
                OR (status='RETRY' AND (next_attempt_at IS NULL OR next_attempt_at <= ?))
                OR (status='PROCESSING' AND lease_until IS NOT NULL AND lease_until < ?)
            )
            ORDER BY id
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """.formatted(t());

        List<Long> ids = jdbc.queryForList(
                selectSql,
                Long.class,
                Timestamp.from(now),
                Timestamp.from(now),
                limit
        );

        if (ids.isEmpty()) return 0;

        String placeholders = ids.stream().map(x -> "?").collect(Collectors.joining(","));
        String updateSql = """
            UPDATE %s
            SET status='PROCESSING', lease_owner=?, lease_until=?
            WHERE id IN (%s)
            """.formatted(t(), placeholders);

        ArrayList<Object> args = new ArrayList<>();
        args.add(owner);
        args.add(Timestamp.from(leaseUntil));
        args.addAll(ids);

        return jdbc.update(updateSql, args.toArray());
    }


}
