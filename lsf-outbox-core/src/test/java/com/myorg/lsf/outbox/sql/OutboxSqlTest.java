package com.myorg.lsf.outbox.sql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class clearOutboxSqlTest {

    @Test
    void valid_tableName() {
        assertEquals("lsf_outbox", OutboxSql.validateTableName("lsf_outbox"));
        assertEquals("LSF_OUTBOX", OutboxSql.validateTableName("LSF_OUTBOX"));
        assertEquals("a1_b2", OutboxSql.validateTableName("a1_b2"));
    }

    @Test
    void valid_schema_tableName() {
        assertEquals("public.lsf_outbox", OutboxSql.validateTableName("public.lsf_outbox"));
        assertEquals("mySchema.TABLE_1", OutboxSql.validateTableName("mySchema.TABLE_1"));
    }

    @Test
    void trim_is_ok() {
        assertEquals("public.lsf_outbox", OutboxSql.validateTableName("  public.lsf_outbox  "));
    }

    @Test
    void reject_blank() {
        assertThrows(IllegalArgumentException.class, () -> OutboxSql.validateTableName(""));
        assertThrows(IllegalArgumentException.class, () -> OutboxSql.validateTableName("   "));
        assertThrows(IllegalArgumentException.class, () -> OutboxSql.validateTableName(null));
    }

    @Test
    void reject_injection_and_invalid_chars() {
        assertThrows(IllegalArgumentException.class,
                () -> OutboxSql.validateTableName("lsf_outbox; DROP TABLE users;"));

        assertThrows(IllegalArgumentException.class,
                () -> OutboxSql.validateTableName("lsf-outbox")); // dấu '-'

        assertThrows(IllegalArgumentException.class,
                () -> OutboxSql.validateTableName("public.lsf_outbox.extra")); // 3 segments

        assertThrows(IllegalArgumentException.class,
                () -> OutboxSql.validateTableName("public.lsf outbox")); // space
    }
}