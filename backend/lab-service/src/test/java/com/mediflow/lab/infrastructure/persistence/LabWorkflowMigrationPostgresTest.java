package com.mediflow.lab.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** V6–V10 on a database holding V5 data: no business backfill, no events, no notices. */
@Testcontainers(disabledWithoutDocker = true)
class LabWorkflowMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test void legacyResultsStayUntouched() {
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        Flyway.configure().dataSource(ds).target("5").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        UUID testId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO lab_test(test_id, record_id, patient_id, requesting_department_id, test_type, requested_date,
                                     performed_date, status, conclusion, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'Công thức máu', DATE '2026-09-01', DATE '2026-09-01', 'COMPLETED', 'Bình thường',
                        now(), now())
                """, testId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        jdbc.update("""
                INSERT INTO lab_result(result_id, test_id, indicator, value, unit, reference_range)
                VALUES (?, ?, 'Hemoglobin', '13.5', 'g/dL', '12-16')
                """, UUID.randomUUID(), testId);
        int outboxBefore = count(jdbc, "lab_outbox_event");

        Flyway.configure().dataSource(ds).load().migrate();

        assertThat(jdbc.queryForObject("SELECT status FROM lab_test WHERE test_id = ?", String.class, testId))
                .isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT result_workflow_version FROM lab_test WHERE test_id = ?", Integer.class,
                testId)).isZero();
        assertThat(jdbc.queryForObject("SELECT value FROM lab_result WHERE test_id = ?", String.class, testId))
                .isEqualTo("13.5");
        assertThat(count(jdbc, "lab_outbox_event")).isEqualTo(outboxBefore);
        assertThat(count(jdbc, "lab_report")).isZero();
        assertThat(count(jdbc, "lab_specimen")).isZero();
        assertThat(count(jdbc, "lab_critical_notice")).isZero();
        assertThat(count(jdbc, "lab_test_template_binding")).isZero();
    }

    /** Release copies template TEXT values and "Không có kết quả: <reason>" into lab_result. */
    @Test void releasedTextValuesFitTheLegacyResultRow() {
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        Flyway.configure().dataSource(ds).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(ds).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        UUID testId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO lab_test(test_id, record_id, patient_id, requesting_department_id, test_type, requested_date,
                                     status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'Nhuộm Gram', DATE '2026-11-01', 'COMPLETED', now(), now())
                """, testId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        String description = "Cầu khuẩn Gram dương xếp chuỗi, ".repeat(20);
        jdbc.update("INSERT INTO lab_result(result_id, test_id, indicator, value) VALUES (?, ?, 'Mô tả', ?)",
                UUID.randomUUID(), testId, description);
        assertThat(jdbc.queryForObject("SELECT value FROM lab_result WHERE test_id = ?", String.class, testId))
                .isEqualTo(description);
    }

    private static int count(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
