package com.mediflow.report.infrastructure.persistence.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Index-only upgrade preserves evidence, generation progress and empty read publication. */
@Testcontainers(disabledWithoutDocker = true)
class ReportTelemetryMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void v13ToV14_preservesRowsAndOnlyAddsFourIndexes() {
        var old = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").target("13").load();
        old.migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.update("INSERT INTO cash_replay_generation(generation_id,status,source_receipts,applied_receipts) VALUES (?,'BUILDING',8,3)", UUID.randomUUID());
        jdbc.update("INSERT INTO daily_visit_report(report_id,report_date,visit_count,revenue) VALUES (?,DATE '2026-10-05',7,50)", UUID.randomUUID());
        String row = jdbc.queryForObject("SELECT row_to_json(g)::text FROM cash_replay_generation g", String.class);
        long tables = jdbc.queryForObject("SELECT count(*) FROM pg_tables WHERE schemaname='public'", Long.class);
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").target("14").load().migrate();
        assertThat(jdbc.queryForObject("SELECT row_to_json(g)::text FROM cash_replay_generation g", String.class)).isEqualTo(row);
        assertThat(jdbc.queryForObject("SELECT sum(visit_count) FROM daily_visit_report", Long.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_tables WHERE schemaname='public'", Long.class)).isEqualTo(tables);
        assertThat(jdbc.queryForList("""
                SELECT indexname FROM pg_indexes WHERE schemaname='public' AND indexname IN
                ('ix_report_pending_close_observed','ix_operational_replay_telemetry',
                    'ix_cash_replay_telemetry','ix_operational_legacy_unverified')
                """, String.class)).hasSize(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operational_report_publication", Long.class)).isZero();
    }
}
