package com.mediflow.clinical.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.mediflow.clinical.domain.model.ConsultationSession;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ClinicIntakeSchemaPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static JdbcTemplate jdbc;
    static ClinicIntakeTestFixtures fixtures;
    static final LocalDate DAY = LocalDate.of(2026, 11, 10);

    @BeforeAll static void migrate() {
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = new JdbcTemplate(ds);
        fixtures = new ClinicIntakeTestFixtures(jdbc);
    }

    @BeforeEach void clear() {
        jdbc.execute("TRUNCATE consultation_session, appointment CASCADE");
    }

    @Test void roomNumberIsUniquePerRoomAndDate() {
        ConsultationSession session = fixtures.session(UUID.randomUUID(), DAY);
        UUID room = session.configuration().roomId();
        ticket(fixtures.registration(session).registrationId(), session, room, DAY, 1);
        assertThatThrownBy(() -> ticket(fixtures.registration(session).registrationId(), session, room, DAY, 1))
                .isInstanceOf(DuplicateKeyException.class);
        ticket(fixtures.registration(session).registrationId(), session, room, DAY.plusDays(1), 1);
        ticket(fixtures.registration(session).registrationId(), session, UUID.randomUUID(), DAY, 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM clinic_queue_ticket", Integer.class)).isEqualTo(3);
    }

    @Test void oneActiveTicketPerRegistration() {
        ConsultationSession session = fixtures.session(UUID.randomUUID(), DAY);
        UUID room = session.configuration().roomId();
        UUID registration = fixtures.registration(session).registrationId();
        UUID first = ticket(registration, session, room, DAY, 1);
        assertThatThrownBy(() -> ticket(registration, session, room, DAY, 2))
                .isInstanceOf(DuplicateKeyException.class);
        jdbc.update("UPDATE clinic_queue_ticket SET status='CANCELLED' WHERE ticket_id=?", first);
        ticket(registration, session, room, DAY, 2);
    }

    @Test void vitalSignsCannotBeUpdatedOrDeleted() {
        UUID registration = fixtures.registration(fixtures.session(UUID.randomUUID(), DAY)).registrationId();
        jdbc.update("""
                INSERT INTO vital_sign_observation(observation_id,registration_id,patient_id,department_id,observed_at,
                    recorded_by_account_id,pulse_bpm) VALUES (?,?,?,?,now(),?,80)
                """, UUID.randomUUID(), registration, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertImmutable("vital_sign_observation", "pulse_bpm = 90");
    }

    @Test void queueAdjustmentsCannotBeUpdatedOrDeleted() {
        ConsultationSession session = fixtures.session(UUID.randomUUID(), DAY);
        UUID ticket = ticket(fixtures.registration(session).registrationId(), session,
                session.configuration().roomId(), DAY, 1);
        jdbc.update("""
                INSERT INTO queue_adjustment(adjustment_id,ticket_id,type,reason,actor_account_id,created_at)
                VALUES (?,?,'LATE','Đến muộn',?,now())
                """, UUID.randomUUID(), ticket, UUID.randomUUID());
        assertImmutable("queue_adjustment", "reason = 'x'");
    }

    @Test void rescheduleLogCannotBeUpdatedOrDeleted() {
        ConsultationSession session = fixtures.session(UUID.randomUUID(), DAY);
        UUID registration = fixtures.registration(session).registrationId();
        jdbc.update("""
                INSERT INTO appointment_reschedule_log(log_id,registration_id,old_session_id,new_session_id,old_doctor_id,
                    new_doctor_id,old_room_id,new_room_id,old_session_date,new_session_date,old_start_time,new_start_time,
                    actor_account_id,reason,patient_confirmed,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'Bác sĩ nghỉ',TRUE,now())
                """, UUID.randomUUID(), registration, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Date.valueOf(DAY), Date.valueOf(DAY),
                java.sql.Time.valueOf(LocalTime.of(8, 0)), java.sql.Time.valueOf(LocalTime.of(9, 0)), UUID.randomUUID());
        assertImmutable("appointment_reschedule_log", "reason = 'x'");
    }

    private void assertImmutable(String table, String assignment) {
        assertThatThrownBy(() -> jdbc.update("UPDATE " + table + " SET " + assignment))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM " + table))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID ticket(UUID registration, ConsultationSession session, UUID room, LocalDate date, int number) {
        UUID id = UUID.randomUUID();
        var config = session.configuration();
        jdbc.update("""
                INSERT INTO clinic_queue_ticket(ticket_id,revision,registration_id,session_id,room_id,department_id,doctor_id,
                    queue_date,room_queue_number,status,priority,issued_by_account_id,issued_at)
                VALUES (?,1,?,?,?,?,?,?,?,'WAITING',FALSE,?,?)
                """, id, registration, session.sessionId(), room, config.departmentId(), config.doctorId(),
                Date.valueOf(date), number, UUID.randomUUID(), Timestamp.from(ClinicIntakeTestFixtures.NOW));
        return id;
    }
}
