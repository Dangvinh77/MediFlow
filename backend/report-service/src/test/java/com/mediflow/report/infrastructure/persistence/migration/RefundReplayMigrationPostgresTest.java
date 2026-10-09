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
class RefundReplayMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine");
    @Test void v16ToV17_preservesLivePendingEvidenceAndOldGrossGeneration_withoutInventedBackfill() {
        migrate("16");
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()));
        UUID generation=UUID.randomUUID();
        jdbc.update("INSERT INTO cash_replay_generation(generation_id,status,completed_at) VALUES (?,'VERIFIED',now())",generation);
        UUID refund=UUID.randomUUID(),event=UUID.randomUUID();
        jdbc.update("""
                INSERT INTO report_cash_refund(refund_transaction_id,original_transaction_id,first_event_id,account_id,
                    patient_id,department_id,care_episode_type,care_episode_id,amount,currency,completed_at_iso,business_date,report_zone,source_fingerprint)
                VALUES (?,?,?,?,?,?,'OUTPATIENT_VISIT',?,20,'VND','2026-10-08T04:00:00Z',DATE '2026-10-08','Asia/Bangkok',?)
                """,refund,UUID.randomUUID(),event,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"a".repeat(64));
        jdbc.update("INSERT INTO report_cash_refund_delivery(event_id,refund_transaction_id,envelope_fingerprint) VALUES (?,?,?)",event,refund,"b".repeat(64));
        String live=jdbc.queryForObject("SELECT row_to_json(r)::text FROM report_cash_refund r",String.class);
        String old=jdbc.queryForObject("SELECT row_to_json(g)::text FROM cash_replay_generation g",String.class);
        migrate("17");
        assertThat(jdbc.queryForObject("SELECT row_to_json(r)::text FROM report_cash_refund r",String.class)).isEqualTo(live);
        assertThat(jdbc.queryForObject("SELECT row_to_json(g)::text FROM cash_replay_generation g",String.class)).isEqualTo(old);
        for (String table:java.util.List.of("refund_replay_generation","refund_replay_input","refund_replay_fact","refund_replay_scope")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM "+table,Long.class)).as(table).isZero();
        }
        assertThatThrownBy(()->jdbc.update("INSERT INTO refund_replay_generation(generation_id,status) VALUES (?,'BUILDING')",UUID.randomUUID()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    private void migrate(String target) {
        Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()).locations("classpath:db/migration").target(target).load().migrate();
    }
}
