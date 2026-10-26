package com.mediflow.clinical.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.clinical.domain.model.OutpatientReceptionMode;
import com.mediflow.clinical.domain.model.OutpatientRegistration;
import com.mediflow.clinical.domain.model.OutpatientRegistrationStatus;
import com.mediflow.clinical.domain.model.OutpatientReservationStatus;
import com.mediflow.clinical.domain.model.OutpatientVisitType;
import com.mediflow.clinical.infrastructure.persistence.adapter.OutpatientRegistrationPersistenceAdapter;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Verifies the follow-up recommendation schema against the Clinical PostgreSQL migrations. */
@Testcontainers(disabledWithoutDocker = true)
class FollowUpRecommendationPersistenceTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void migrationCreatesRecommendationAndAuditTables() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThat(tableExists(jdbc, "follow_up_recommendation"))
                .as("durable recommendation source")
                .isTrue();
        assertThat(tableExists(jdbc, "follow_up_recommendation_audit"))
                .as("recommendation creation audit")
                .isTrue();
        assertThat(columnExists(jdbc, "outpatient_registration", "follow_up_recommendation_id")).isTrue();
        assertThat(columnExists(jdbc, "outpatient_registration", "patient_agreed_to_doctor_replacement")).isTrue();
    }

    @Test
    void recommendationIsUniquePerCompletedSourceAndBoundToItsExactClinicalIdentities() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Source source = insertCompletedV2Source(jdbc);
        insertRecommendation(jdbc, source, UUID.randomUUID());

        assertThatThrownBy(() -> insertRecommendation(jdbc, source, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        Source separateSource = insertCompletedV2Source(jdbc);
        assertThatThrownBy(() -> insertRecommendation(jdbc,
                new Source(separateSource.recordId(), separateSource.registrationId(), UUID.randomUUID(),
                        separateSource.departmentId(), separateSource.doctorId(), separateSource.sessionId()),
                UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void followUpRecommendationAuditAndRegistrationSnapshotAreImmutableAndForeignKeyBound() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Source source = insertCompletedV2Source(jdbc);
        UUID recommendationId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        insertRecommendation(jdbc, source, recommendationId);
        insertRecommendationOutboxEvent(jdbc, recommendationId, eventId);
        insertRecommendationAudit(jdbc, recommendationId, eventId);
        UUID followUpRegistrationId = insertFollowUpRegistration(jdbc, source, recommendationId);

        assertThat(jdbc.queryForObject("""
                SELECT after_state #>> '{followUp,previousRegistrationId}'
                FROM outpatient_registration_audit WHERE registration_id = ?
                """, String.class, followUpRegistrationId)).isEqualTo(source.registrationId().toString());
        assertThat(jdbc.queryForObject("""
                SELECT after_state #>> '{followUp,recommendationId}'
                FROM outpatient_registration_audit WHERE registration_id = ?
                """, String.class, followUpRegistrationId)).isEqualTo(recommendationId.toString());

        assertThatThrownBy(() -> jdbc.update("""
                UPDATE follow_up_recommendation SET reason = 'changed' WHERE recommendation_id = ?
                """, recommendationId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM follow_up_recommendation WHERE recommendation_id = ?",
                recommendationId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE follow_up_recommendation_audit SET after_state = '{}'::jsonb WHERE event_id = ?
                """, eventId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM follow_up_recommendation_audit WHERE event_id = ?",
                eventId)).isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("UPDATE outpatient_registration SET status = 'EXPIRED' WHERE registration_id = ?",
                followUpRegistrationId);
        assertThat(jdbc.queryForObject("""
                SELECT follow_up_recommendation_id FROM outpatient_registration WHERE registration_id = ?
                """, UUID.class, followUpRegistrationId)).isEqualTo(recommendationId);
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE outpatient_registration SET follow_up_recommendation_id = NULL
                WHERE registration_id = ?
                """, followUpRegistrationId)).isInstanceOf(DataIntegrityViolationException.class);

        Source otherPatientSource = insertCompletedV2Source(jdbc);
        assertThatThrownBy(() -> insertMismatchedFollowUpRegistration(jdbc, otherPatientSource, recommendationId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void closedNotPaidReceiptCannotRetainAnyResolutionResultField() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Source source = insertCompletedV2Source(jdbc);
        for (String resultField : List.of("transaction_id", "clearance_id", "completed_at")) {
            UUID operationId = insertPaymentLockedReceipt(jdbc, source);
            String staleResultAssignment = switch (resultField) {
                case "transaction_id", "clearance_id" -> resultField + " = '" + UUID.randomUUID() + "'";
                case "completed_at" -> "completed_at = TIMESTAMPTZ '2026-10-26 09:00:00Z'";
                default -> throw new IllegalStateException("Unexpected result field: " + resultField);
            };

            assertThatThrownBy(() -> jdbc.update("""
                    UPDATE outpatient_payment_operation_receipt
                    SET state = 'CLOSED_NOT_PAID', close_reason = 'RESERVATION_EXPIRED', %s
                    WHERE payment_operation_id = ?
                    """.formatted(staleResultAssignment), operationId))
                    .as("CLOSED_NOT_PAID must not retain %s", resultField)
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    private static boolean tableExists(JdbcTemplate jdbc, String tableName) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.tables
                    WHERE table_schema = current_schema() AND table_name = ?)
                """, Boolean.class, tableName));
    }

    private static boolean columnExists(JdbcTemplate jdbc, String tableName, String columnName) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?)
                """, Boolean.class, tableName, columnName));
    }

    private static Source insertCompletedV2Source(JdbcTemplate jdbc) {
        UUID registrationId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO appointment(appointment_id, patient_id, doctor_id, department_id, appointment_date,
                    appointment_time, status, reason, created_at, care_contract_version, exam_clearance_id,
                    exam_price_code, completed_at)
                VALUES (?, ?, ?, ?, DATE '2026-10-20', TIME '10:00', 'COMPLETED', NULL,
                    TIMESTAMPTZ '2026-10-20 10:00:00Z', 2, ?, 'OUTPATIENT_EXAM', TIMESTAMPTZ '2026-10-20 11:00:00Z')
                """, registrationId, patientId, doctorId, departmentId, UUID.randomUUID());
        jdbc.update("""
                INSERT INTO consultation_session(session_id, revision, doctor_id, department_id, room_id,
                    session_date, start_time, end_time, breaks, capacity, status, available_count, created_at, updated_at)
                VALUES (?, 1, ?, ?, ?, DATE '2026-10-20', TIME '09:00', TIME '11:00', '[]'::jsonb,
                    1, 'OPEN', 1, TIMESTAMPTZ '2026-10-20 08:00:00Z', TIMESTAMPTZ '2026-10-20 08:00:00Z')
                """, sessionId, doctorId, departmentId, UUID.randomUUID());
        jdbc.update("""
                INSERT INTO outpatient_registration(registration_id, session_id, patient_id, revision, reception_mode,
                    visit_type, status, reservation_id, reservation_revision, reservation_status,
                    reservation_expires_at, queue_number, created_at, updated_at)
                VALUES (?, ?, ?, 1, 'AT_COUNTER', 'NEW_VISIT', 'COMPLETED', ?, 1, 'CONSUMED',
                    TIMESTAMPTZ '2026-10-20 09:00:00Z', 1, TIMESTAMPTZ '2026-10-20 08:00:00Z',
                    TIMESTAMPTZ '2026-10-20 11:00:00Z')
                """, registrationId, sessionId, patientId, reservationId);
        jdbc.update("""
                INSERT INTO medical_record(record_id, patient_id, doctor_id, department_id, examination_date,
                    appointment_id, created_at, status, disposition, completed_at)
                VALUES (?, ?, ?, ?, DATE '2026-10-20', ?, TIMESTAMPTZ '2026-10-20 10:00:00Z',
                    'COMPLETED', 'FOLLOW_UP', TIMESTAMPTZ '2026-10-20 11:00:00Z')
                """, recordId, patientId, doctorId, departmentId, registrationId);
        return new Source(recordId, registrationId, patientId, departmentId, doctorId, sessionId);
    }

    private static void insertRecommendation(JdbcTemplate jdbc, Source source, UUID recommendationId) {
        jdbc.update("""
                INSERT INTO follow_up_recommendation(recommendation_id, source_record_id, source_registration_id,
                    patient_id, department_id, preferred_doctor_id, recommended_from, recommended_through,
                    reason, created_by_account_id, created_by_staff_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, DATE '2026-10-20', DATE '2026-10-21', 'Review symptoms', ?, ?,
                    TIMESTAMPTZ '2026-10-26 02:00:00Z')
                """, recommendationId, source.recordId(), source.registrationId(), source.patientId(),
                source.departmentId(), source.doctorId(), UUID.randomUUID(), source.doctorId());
    }

    private static UUID insertPaymentLockedReceipt(JdbcTemplate jdbc, Source source) {
        UUID operationId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO outpatient_payment_operation_receipt(payment_operation_id, payment_request_id,
                    registration_id, reservation_id, patient_id, source_type, source_id, care_episode_id,
                    expected_reservation_revision, lease_reservation_revision, fence_token, state,
                    locked_at, reconcile_after, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'EXAM', ?, ?, 1, 1, 1, 'PAYMENT_LOCKED',
                    TIMESTAMPTZ '2026-10-26 08:00:00Z', TIMESTAMPTZ '2026-10-26 08:15:00Z',
                    TIMESTAMPTZ '2026-10-26 08:00:00Z', TIMESTAMPTZ '2026-10-26 08:00:00Z')
                """, operationId, UUID.randomUUID(), source.registrationId(), UUID.randomUUID(),
                source.patientId(), source.registrationId(), source.registrationId());
        return operationId;
    }

    private static UUID insertFollowUpRegistration(JdbcTemplate jdbc, Source previous, UUID recommendationId) {
        UUID registrationId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-26T08:00:00Z");
        jdbc.update("""
                INSERT INTO appointment(appointment_id, patient_id, doctor_id, department_id, appointment_date,
                    appointment_time, status, reason, created_at, care_contract_version, exam_price_code)
                VALUES (?, ?, ?, ?, DATE '2026-10-27', TIME '10:00', 'AWAITING_PAYMENT', 'Follow-up visit',
                    TIMESTAMPTZ '2026-10-26 08:00:00Z', 2, 'OUTPATIENT_EXAM')
                """, registrationId, previous.patientId(), previous.doctorId(), previous.departmentId());
        var reservation = new OutpatientRegistration.Reservation(UUID.randomUUID(), 1,
                OutpatientReservationStatus.HELD, now.plusSeconds(900), null, null,
                null, null, null, null, null);
        var registration = new OutpatientRegistration(registrationId, 1, previous.patientId(), previous.sessionId(),
                OutpatientReceptionMode.AT_COUNTER, OutpatientVisitType.FOLLOW_UP,
                OutpatientRegistrationStatus.AWAITING_PAYMENT, now, null, now, now, reservation, null,
                new OutpatientRegistration.FollowUpLink(previous.registrationId(), recommendationId, null, null));
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        new OutpatientRegistrationPersistenceAdapter(jdbc, mapper).insert(registration,
                UUID.randomUUID(), UUID.randomUUID(), "CREATED", now);
        return registrationId;
    }

    private static void insertMismatchedFollowUpRegistration(JdbcTemplate jdbc, Source previous,
            UUID recommendationId) {
        UUID registrationId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO appointment(appointment_id, patient_id, doctor_id, department_id, appointment_date,
                    appointment_time, status, reason, created_at, care_contract_version, exam_price_code)
                VALUES (?, ?, ?, ?, DATE '2026-10-27', TIME '10:00', 'AWAITING_PAYMENT', 'Follow-up visit',
                    TIMESTAMPTZ '2026-10-26 08:00:00Z', 2, 'OUTPATIENT_EXAM')
                """, registrationId, previous.patientId(), previous.doctorId(), previous.departmentId());
        jdbc.update("""
                INSERT INTO outpatient_registration(registration_id, session_id, patient_id, revision, reception_mode,
                    visit_type, status, reservation_id, reservation_revision, reservation_status,
                    reservation_expires_at, created_at, updated_at, previous_registration_id,
                    follow_up_recommendation_id)
                VALUES (?, ?, ?, 1, 'AT_COUNTER', 'FOLLOW_UP', 'AWAITING_PAYMENT', ?, 1, 'HELD',
                    TIMESTAMPTZ '2026-10-26 08:15:00Z', TIMESTAMPTZ '2026-10-26 08:00:00Z',
                    TIMESTAMPTZ '2026-10-26 08:00:00Z', ?, ?)
                """, registrationId, previous.sessionId(), previous.patientId(), UUID.randomUUID(),
                previous.registrationId(), recommendationId);
    }

    private static void insertRecommendationOutboxEvent(JdbcTemplate jdbc, UUID recommendationId, UUID eventId) {
        jdbc.update("""
                INSERT INTO clinical_outbox_event(event_id, aggregate_type, aggregate_id, event_type, event_version,
                    correlation_id, payload, occurred_at, retry_count)
                VALUES (?, 'FOLLOW_UP_RECOMMENDATION', ?, 'followup.recommendation.created', 1,
                    'correlation-test', '{}'::jsonb, TIMESTAMPTZ '2026-10-26 02:00:00Z', 0)
                """, eventId, recommendationId);
    }

    private static void insertRecommendationAudit(JdbcTemplate jdbc, UUID recommendationId, UUID eventId) {
        jdbc.update("""
                INSERT INTO follow_up_recommendation_audit(audit_id, recommendation_id, action,
                    actor_account_id, actor_staff_id, correlation_id, event_id, after_state, occurred_at)
                VALUES (?, ?, 'CREATED', ?, ?, 'correlation-test', ?, '{}'::jsonb,
                    TIMESTAMPTZ '2026-10-26 02:00:00Z')
                """, UUID.randomUUID(), recommendationId, UUID.randomUUID(), UUID.randomUUID(), eventId);
    }

    private record Source(UUID recordId, UUID registrationId, UUID patientId, UUID departmentId,
            UUID doctorId, UUID sessionId) {}
}
