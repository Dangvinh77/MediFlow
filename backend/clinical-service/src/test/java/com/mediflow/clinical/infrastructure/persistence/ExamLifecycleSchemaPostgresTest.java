package com.mediflow.clinical.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Date;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class ExamLifecycleSchemaPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static JdbcTemplate jdbc;

    @BeforeAll static void migrate() {
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = new JdbcTemplate(ds);
    }

    private UUID record() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO medical_record(record_id,patient_id,doctor_id,department_id,examination_date) VALUES (?,?,?,?,?)",
                id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Date.valueOf("2026-11-10"));
        return id;
    }

    private int close(UUID id, String extra) {
        return jdbc.update("UPDATE medical_record SET status='CLOSED_WITHOUT_CONCLUSION'" + extra + " WHERE record_id=?", id);
    }

    private static final String FULL = ",closure_reason='PATIENT_LEFT',closure_note='Tự về',closed_by_staff_id=gen_random_uuid(),closed_at=now()";

    @Test void fullClosureIsAccepted() {
        assertThat(close(record(), FULL)).isEqualTo(1);
    }

    @Test void closedRecordRequiresAllClosureFields() {
        UUID id = record();
        assertThatThrownBy(() -> close(id, ",closure_reason='PATIENT_LEFT'")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void closedRecordCannotCarryDisposition() {
        UUID id = record();
        assertThatThrownBy(() -> close(id, FULL + ",disposition='OTHER'")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void openRecordCannotCarryClosure() {
        UUID id = record();
        assertThatThrownBy(() -> jdbc.update("UPDATE medical_record SET closure_note='x' WHERE record_id=?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void existingStatusesStillValid() {
        UUID id = record();
        assertThat(jdbc.update("UPDATE medical_record SET status='COMPLETED',disposition='OTHER',completed_at=now() WHERE record_id=?", id)).isEqualTo(1);
    }

    @Test void labOrderWithdrawalFieldsGoTogether() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_constraint WHERE conname = 'ck_clinical_lab_order_withdrawal'
                """, Integer.class)).isEqualTo(1);
    }

    @Test void returnSourceAndReasonGoTogether() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_constraint WHERE conname = 'ck_outpatient_registration_return_link'
                """, Integer.class)).isEqualTo(1);
    }
}
