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

@Testcontainers(disabledWithoutDocker = true)
class OperationalSnapshotReadMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test void v17ToV18_preservesLegacyAndSnapshotRowsWithoutPublishingOrBackfilling() {
        migration("17").migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        UUID generation = UUID.randomUUID();
        jdbc.update("INSERT INTO operational_replay_generation(generation_id,status,completed_at) VALUES (?,'VERIFIED',now())", generation);
        jdbc.update("INSERT INTO operational_replay_scope(generation_id,metric_date,department_id,metric_type,numeric_value) VALUES (?,DATE '2026-10-09',NULL,'COMPLETED_VISITS',17)", generation);
        jdbc.update("INSERT INTO daily_visit_report(report_id,report_date,visit_count,revenue) VALUES (?,DATE '2026-10-09',4,123.45)", UUID.randomUUID());
        String snapshot = jdbc.queryForObject("SELECT row_to_json(s)::text FROM operational_replay_scope s", String.class);
        String legacy = jdbc.queryForObject("SELECT row_to_json(s)::text FROM daily_visit_report s", String.class);
        var oldChecksums = jdbc.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");
        long tables = jdbc.queryForObject("SELECT count(*) FROM pg_tables WHERE schemaname='public'", Long.class);
        assertThat(migration("18").migrate().migrationsExecuted).isOne();
        assertThat(jdbc.queryForObject("SELECT row_to_json(s)::text FROM operational_replay_scope s", String.class)).isEqualTo(snapshot);
        assertThat(jdbc.queryForObject("SELECT row_to_json(s)::text FROM daily_visit_report s", String.class)).isEqualTo(legacy);
        assertThat(jdbc.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE version IS NOT NULL AND version<>'18' ORDER BY installed_rank"))
                .isEqualTo(oldChecksums);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_tables WHERE schemaname='public'", Long.class)).isEqualTo(tables);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operational_report_publication", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operational_event_journal", Long.class)).isZero();
        assertThat(migration("18").migrate().migrationsExecuted).isZero();
        migration("18").validate();
    }

    private Flyway migration(String version) {
        return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").target(version).load();
    }
}
