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

/** Switching resource location preserves released checksums and accepted writes, without repair. */
@Testcontainers(disabledWithoutDocker=true)
class ReportModuleUpgradePostgresTest {
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine");
    @Test void standaloneV26ToNamespacedV27PreservesHistoryAndRollbackReadability() {
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()));
        var original=Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword())
                .locations("classpath:db/migration").target("26").cleanDisabled(true).load();
        original.migrate();
        UUID legacy=UUID.randomUUID();
        jdbc.update("INSERT INTO daily_visit_report(report_id,report_date,visit_count) VALUES (?,DATE '2026-10-01',3)",legacy);
        var before=jdbc.queryForList("SELECT version,script,checksum FROM flyway_schema_history WHERE success ORDER BY installed_rank");
        var module=Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword())
                .locations("classpath:mediflow/report/db/migration").cleanDisabled(true).load();
        assertThat(module.migrate().migrationsExecuted).isOne();
        assertThat(module.validateWithResult().validationSuccessful).isTrue();
        assertThat(jdbc.queryForList("SELECT version,script,checksum FROM flyway_schema_history WHERE version<>'27' AND success ORDER BY installed_rank"))
                .containsExactlyElementsOf(before);
        assertThat(jdbc.queryForObject("SELECT visit_count FROM daily_visit_report WHERE report_id=?",Integer.class,legacy)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_outpatient_coverage",Integer.class)).isZero();
        // A new accepted write is retained when changing back to the standalone migration location.
        jdbc.update("UPDATE daily_visit_report SET visit_count=4 WHERE report_id=?",legacy);
        var rollback=Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword())
                .locations("classpath:db/migration").cleanDisabled(true).load();
        assertThat(rollback.validateWithResult().validationSuccessful).isTrue();
        assertThat(rollback.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT visit_count FROM daily_visit_report WHERE report_id=?",Integer.class,legacy)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success",Integer.class)).isEqualTo(24);
    }
}
