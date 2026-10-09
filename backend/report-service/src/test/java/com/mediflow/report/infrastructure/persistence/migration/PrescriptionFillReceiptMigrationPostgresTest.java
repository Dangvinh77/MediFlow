package com.mediflow.report.infrastructure.persistence.migration;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class PrescriptionFillReceiptMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void v15ToV16_preservesHistoricalTotals_andDoesNotInventSourceProof() {
        migrate("15");
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
        jdbc.update("INSERT INTO daily_visit_report(report_id,report_date,department_id,prescription_count) VALUES (?,DATE '2026-10-07',?,5)",
                UUID.randomUUID(), UUID.randomUUID());
        UUID existingDelivery = UUID.randomUUID();
        jdbc.update("INSERT INTO processed_event(event_id,routing_key) VALUES (?,'prescription.filled')", existingDelivery);
        String original = jdbc.queryForObject("SELECT row_to_json(r)::text FROM daily_visit_report r", String.class);
        migrate("16");
        assertThat(jdbc.queryForObject("SELECT row_to_json(r)::text FROM daily_visit_report r", String.class)).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_event WHERE event_id=?", Integer.class, existingDelivery)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM prescription_fill_receipt", Integer.class)).isZero();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO prescription_fill_receipt(prescription_id,fact_fingerprint) VALUES (?,?)",
                UUID.randomUUID(), "not-a-proof")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private void migrate(String target) {
        Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .locations("classpath:db/migration").target(target).load().migrate();
    }
}
