package com.mediflow.report.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Inventory must not recover/reject an early refund or manufacture accepted coverage. */
@Testcontainers(disabledWithoutDocker = true)
class MvpCutoverInventoryPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test void inventoryCompilesOnMigratedStoreAndPreservesPendingRefundAndEmptyCoverage() throws Exception {
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(ds);
        UUID refund = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO report_cash_refund(refund_transaction_id,original_transaction_id,first_event_id,
                    account_id,patient_id,department_id,care_episode_type,care_episode_id,amount,currency,
                    completed_at_iso,business_date,report_zone,source_fingerprint)
                VALUES (?,?,?,?,?,?,'OUTPATIENT_VISIT',?,1,'VND','2026-10-01T01:00:00Z',
                    DATE '2026-10-01','Asia/Bangkok',?)
                """, refund, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64));
        var before = jdbc.queryForMap("SELECT * FROM report_cash_refund WHERE refund_transaction_id=?", refund);
        String script;
        try (var input = getClass().getResourceAsStream("/operations/mvp-cutover-inventory.sql")) {
            script = new String(input.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?m)--.*$", "");
        }
        int results = 0;
        boolean pending = false;
        try (var connection = ds.getConnection(); var statement = connection.createStatement()) {
            for (String sql : script.split(";")) {
                if (sql.isBlank() || !statement.execute(sql)) continue;
                results++;
                try (var rows = statement.getResultSet()) {
                    if (results == 1) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getString("read_only")).isEqualTo("on");
                        assertThat(rows.getString("isolation_level")).isEqualTo("repeatable read");
                    } else if (results == 3) {
                        while (rows.next()) pending |= "PENDING".equals(rows.getString("refund_state")) && rows.getLong("refund_count") == 1;
                    }
                }
            }
        }
        assertThat(results).isEqualTo(7);
        assertThat(pending).isTrue();
        assertThat(jdbc.queryForMap("SELECT * FROM report_cash_refund WHERE refund_transaction_id=?", refund)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_outpatient_coverage", Integer.class)).isZero();
    }
}
