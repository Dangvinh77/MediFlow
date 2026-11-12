package com.mediflow.clinical.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Rows written before V17 keep their meaning, and a visit still open then can be closed afterwards. */
@Testcontainers(disabledWithoutDocker = true)
class ExamClosureMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static final UUID OPEN = UUID.randomUUID();
    static final UUID COMPLETED = UUID.randomUUID();
    static JdbcTemplate jdbc;

    private static void insert(UUID id) {
        jdbc.update("INSERT INTO medical_record(record_id,patient_id,doctor_id,department_id,examination_date) VALUES (?,?,?,?,?)",
                id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Date.valueOf("2026-11-09"));
    }

    @BeforeAll static void migrateAcrossV17() {
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        jdbc = new JdbcTemplate(ds);
        Flyway.configure().dataSource(ds).target("16").load().migrate();
        insert(OPEN);
        insert(COMPLETED);
        jdbc.update("UPDATE medical_record SET status='COMPLETED',disposition='OTHER',completed_at=now() WHERE record_id=?", COMPLETED);
        Flyway.configure().dataSource(ds).load().migrate();
    }

    private static String status(UUID id) {
        return jdbc.queryForObject("SELECT status FROM medical_record WHERE record_id=?", String.class, id);
    }

    @Test void completedRecordsStayCompletedWithoutClosureData() {
        assertThat(status(COMPLETED)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT closure_reason FROM medical_record WHERE record_id=?", String.class, COMPLETED)).isNull();
    }

    @Test void recordOpenBeforeTheMigrationCanBeClosed() {
        assertThat(status(OPEN)).isEqualTo("OPEN");
        assertThat(jdbc.update("""
                UPDATE medical_record SET status='CLOSED_WITHOUT_CONCLUSION',closure_reason='PATIENT_LEFT',closure_note='Tự về',
                closed_by_staff_id=gen_random_uuid(),closed_at=now() WHERE record_id=?
                """, OPEN)).isEqualTo(1);
    }
}
