package com.mediflow.clinical.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** A scheduled (contract 2) appointment carries the same cancellation audit as contract 1. */
@Testcontainers(disabledWithoutDocker = true)
class AppointmentCancellationAuditPostgresTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcTemplate migrated() {
        DriverManagerDataSource source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        source.setDriverClassName("org.postgresql.Driver");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        return new JdbcTemplate(source);
    }

    private UUID scheduledAppointment(JdbcTemplate jdbc) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO appointment (appointment_id, patient_id, doctor_id, department_id, appointment_date,"
                + " appointment_time, status, care_contract_version, exam_price_code)"
                + " VALUES (?, ?, ?, ?, ?, ?, 'AWAITING_PAYMENT', 2, 'OUTPATIENT_EXAM')",
                id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 11, 10),
                LocalTime.of(8, 0));
        return id;
    }

    @Test
    void scheduledAppointmentCanRecordItsCancellation() {
        JdbcTemplate jdbc = migrated();
        UUID id = scheduledAppointment(jdbc);

        int updated = jdbc.update("UPDATE appointment SET status='CANCELLED', cancelled_by=?,"
                + " cancellation_reason='Bệnh nhân đổi ý', cancelled_at=now() + interval '1 minute'"
                + " WHERE appointment_id=?", UUID.randomUUID(), id);

        assertThat(updated).isEqualTo(1);
    }

    @Test
    void cancellationAuditStaysAllOrNothing() {
        JdbcTemplate jdbc = migrated();
        UUID id = scheduledAppointment(jdbc);

        assertThatThrownBy(() -> jdbc.update("UPDATE appointment SET status='CANCELLED', cancelled_by=?"
                + " WHERE appointment_id=?", UUID.randomUUID(), id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
