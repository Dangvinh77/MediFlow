package com.mediflow.report.infrastructure.persistence.migration;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class OperationalPublicationMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @BeforeEach void cleanIsolatedContainer() { migration("latest").clean(); }

    @Test void freshV26_startsWithNoFabricatedAcceptanceOrPublication() {
        var migrate = migration("latest"); migrate.migrate(); migrate.validate();
        var jdbc = jdbc();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operational_generation_acceptance", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operational_publication_audit", Integer.class)).isZero();
        assertThat(new com.mediflow.report.infrastructure.persistence.adapter.OperationalSnapshotReadPersistenceAdapter(jdbc)
                .publication("SURGERY")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operational_report_publication", Integer.class)).isZero();
    }

    @Test void v25Upgrade_preservesOldRowsAndChecksumsWithoutAdoptingManualPointers() {
        migration("25").migrate(); var jdbc = jdbc(); UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO operational_replay_generation(generation_id,status,completed_at) VALUES (?,'VERIFIED',now())", id);
        jdbc.update("INSERT INTO operational_replay_scope(generation_id,metric_date,metric_type,numeric_value) VALUES (?,DATE '2026-10-07','SURGERIES_COMPLETED',7)", id);
        jdbc.update("INSERT INTO operational_report_publication(report_kind,generation_id,covered_from,covered_to,zone_id,metrics,acceptance_reference) "
                + "VALUES ('SURGERY',?,DATE '2026-10-07',DATE '2026-10-07','Asia/Bangkok',ARRAY['SURGERIES_COMPLETED'],'old/manual')", id);
        jdbc.update("INSERT INTO daily_visit_report(report_id,report_date,visit_count,revenue) VALUES (?,DATE '2026-10-07',4,123.45)", UUID.randomUUID());
        var scopes = jdbc.queryForList("SELECT to_jsonb(t)::text FROM operational_replay_scope t", String.class);
        var pointer = jdbc.queryForList("SELECT to_jsonb(t)::text FROM operational_report_publication t", String.class);
        var legacy = jdbc.queryForList("SELECT to_jsonb(t)::text FROM daily_visit_report t", String.class);
        var checksums = jdbc.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");
        var migrate = migration("latest"); assertThat(migrate.migrate().migrationsExecuted).isOne(); migrate.validate();
        assertThat(jdbc.queryForList("SELECT to_jsonb(t)::text FROM operational_replay_scope t", String.class)).isEqualTo(scopes);
        assertThat(jdbc.queryForList("SELECT to_jsonb(t)::text FROM operational_report_publication t", String.class)).isEqualTo(pointer);
        assertThat(jdbc.queryForList("SELECT to_jsonb(t)::text FROM daily_visit_report t", String.class)).isEqualTo(legacy);
        assertThat(jdbc.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE version IS NOT NULL AND version<>'26' ORDER BY installed_rank")).isEqualTo(checksums);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operational_generation_acceptance", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operational_publication_audit", Integer.class)).isZero();
        assertThat(new com.mediflow.report.infrastructure.persistence.adapter.OperationalSnapshotReadPersistenceAdapter(jdbc)
                .publication("SURGERY")).isEmpty();
    }
    private JdbcTemplate jdbc() { return new JdbcTemplate(new DriverManagerDataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword())); }
    private Flyway migration(String target) {
        return Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword())
                .locations("classpath:db/migration").target(target).cleanDisabled(false).load();
    }
}
