package com.mediflow.report.infrastructure.persistence.migration;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CashRefundMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Test void v14ToV15_preservesExistingReceiptHashesAndGrossTotalsAllowsDurableEarlyRefund() {
        migrate("14");
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
        UUID original = UUID.randomUUID(), patient = UUID.randomUUID(), account = UUID.randomUUID(), department = UUID.randomUUID(), episode = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO report_cash_receipt(transaction_id,first_event_id,payment_request_id,account_id,patient_id,
                    department_id,care_episode_type,care_episode_id,classification,amount,currency,payment_method,
                    completed_at,completed_at_iso,business_date,report_zone,payload_fingerprint,fact_fingerprint)
                VALUES (?,?,?,?,?,?,'OUTPATIENT_VISIT',?,'SERVICE_PAYMENT',100,'VND','CASH',
                    TIMESTAMPTZ '2026-10-05T08:00:00Z','2026-10-05T08:00:00Z',DATE '2026-10-05','Asia/Bangkok',?,?)
                """, original, UUID.randomUUID(), UUID.randomUUID(), account, patient, department, episode, "a".repeat(64), "b".repeat(64));
        jdbc.update("""
                INSERT INTO report_gross_cash_daily(scope_id,business_date,currency,report_zone,department_id,classification,gross_receipts,receipt_count)
                VALUES (?,DATE '2026-10-05','VND','Asia/Bangkok',?,'SERVICE_PAYMENT',100,1)
                """, UUID.randomUUID(), department);
        String oldReceipt = jdbc.queryForObject("SELECT row_to_json(p)::text FROM report_cash_receipt p", String.class);
        String oldTotal = jdbc.queryForObject("SELECT row_to_json(p)::text FROM report_gross_cash_daily p", String.class);
        migrate("15");
        assertThat(jdbc.queryForObject("SELECT row_to_json(p)::text FROM report_cash_receipt p", String.class)).isEqualTo(oldReceipt);
        assertThat(jdbc.queryForObject("SELECT row_to_json(p)::text FROM report_gross_cash_daily p", String.class)).isEqualTo(oldTotal);
        UUID refund = UUID.randomUUID(), absentOriginal = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO report_cash_refund(refund_transaction_id,original_transaction_id,first_event_id,account_id,
                    patient_id,department_id,care_episode_type,care_episode_id,amount,currency,completed_at_iso,business_date,report_zone,source_fingerprint)
                VALUES (?,?,?,?,?,?,'OUTPATIENT_VISIT',?,20,'VND','2026-10-08T04:00:00Z',DATE '2026-10-08','Asia/Bangkok',?)
                """, refund, absentOriginal, UUID.randomUUID(), account, patient, department, episode, "c".repeat(64));
        assertThat(jdbc.queryForObject("SELECT state FROM report_cash_refund", String.class)).isEqualTo("PENDING");
        assertThatThrownBy(() -> jdbc.update("UPDATE report_cash_refund SET state='APPLIED' WHERE refund_transaction_id=?", refund))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_refund_cash_daily", Long.class)).isZero();
    }
    private void migrate(String target) { Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
            .locations("classpath:db/migration").target(target).load().migrate(); }
}
