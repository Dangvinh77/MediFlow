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
    @Test void standaloneV26ToNamespacedV29PreservesHistoryAndRollbackReadability() {
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()));
        var original=Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword())
                .locations("classpath:db/migration").target("26").cleanDisabled(true).load();
        original.migrate();
        UUID legacy=UUID.randomUUID();
        jdbc.update("INSERT INTO daily_visit_report(report_id,report_date,visit_count) VALUES (?,DATE '2026-10-01',3)",legacy);
        var before=jdbc.queryForList("SELECT version,script,checksum FROM flyway_schema_history WHERE success ORDER BY installed_rank");
        var module=Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword())
                .locations("classpath:mediflow/report/db/migration").cleanDisabled(true).load();
        assertThat(module.migrate().migrationsExecuted).isEqualTo(3);
        assertThat(module.validateWithResult().validationSuccessful).isTrue();
        assertThat(jdbc.queryForList("SELECT version,script,checksum FROM flyway_schema_history WHERE version NOT IN ('27','28','29') AND success ORDER BY installed_rank"))
                .containsExactlyElementsOf(before);
        assertThat(jdbc.queryForObject("SELECT visit_count FROM daily_visit_report WHERE report_id=?",Integer.class,legacy)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_outpatient_coverage",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_outpatient_coverage_acceptance",Integer.class)).isZero();
        // A new accepted write is retained when changing back to the standalone migration location.
        jdbc.update("UPDATE daily_visit_report SET visit_count=4 WHERE report_id=?",legacy);
        var rollback=Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword())
                .locations("classpath:db/migration").cleanDisabled(true).load();
        assertThat(rollback.validateWithResult().validationSuccessful).isTrue();
        assertThat(rollback.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT visit_count FROM daily_visit_report WHERE report_id=?",Integer.class,legacy)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success",Integer.class)).isEqualTo(26);
    }

    @Test void v28UpgradeBackfillsStableOrderWithoutChangingCoverageOrCreatingReceipts() {
        String schema="coverage_order_"+UUID.randomUUID().toString().replace("-","");
        var beforeMigration=Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword())
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("28").load();
        beforeMigration.migrate();
        String url=PG.getJdbcUrl()+(PG.getJdbcUrl().contains("?")?"&":"?")+"currentSchema="+schema;
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(url,PG.getUsername(),PG.getPassword()));
        UUID lower=UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID higher=UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID later=UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        insertCoverage(jdbc,lower,"LEGACY_LOW_ID");
        insertCoverage(jdbc,higher,"LEGACY_HIGH_ID");

        var upgrade=Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword())
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load();
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_outpatient_coverage",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_outpatient_coverage_acceptance",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT acceptance_reference FROM report_outpatient_coverage WHERE coverage_id=?",String.class,lower))
                .isEqualTo("LEGACY_LOW_ID");
        assertThat(jdbc.queryForObject("SELECT coverage_id FROM report_outpatient_coverage ORDER BY acceptance_order DESC LIMIT 1",UUID.class))
                .isEqualTo(lower);

        insertCoverage(jdbc,later,"NEWER_EQUAL_TIMESTAMP");
        assertThat(jdbc.queryForObject("SELECT coverage_id FROM report_outpatient_coverage ORDER BY acceptance_order DESC LIMIT 1",UUID.class))
                .isEqualTo(later);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_outpatient_coverage",Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_outpatient_coverage_acceptance",Integer.class)).isZero();
    }

    private void insertCoverage(JdbcTemplate jdbc, UUID id, String reference) {
        jdbc.update("""
                INSERT INTO report_outpatient_coverage(coverage_id,covered_from,covered_to,zone_id,currency,source_references,
                    source_observed_at,reconciled_at,accepted_at,accepted_by,acceptance_reference,local_evidence_fingerprint)
                VALUES (?,DATE '2026-10-01',DATE '2026-10-01','Asia/Bangkok','VND',
                    '{"CLINICAL":"archive-clinical","LAB":"archive-lab","PHARMACY":"archive-pharmacy",
                      "BILLING_CASH":"archive-cash","BILLING_REFUND":"archive-refund"}'::jsonb,
                    TIMESTAMPTZ '2026-10-02T00:00:00Z',TIMESTAMPTZ '2026-10-02T01:00:00Z',
                    TIMESTAMPTZ '2026-10-03T00:00:00Z',?,?,?)
                """,id,UUID.randomUUID(),reference,"a".repeat(64));
    }
}
