package com.mediflow.clinical.infrastructure.persistence.adapter;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.dto.response.ReservationPaymentContext;
import com.mediflow.clinical.application.port.out.OutpatientRegistrationRepositoryPort;
import com.mediflow.clinical.domain.model.OutpatientReceptionMode;
import com.mediflow.clinical.domain.model.OutpatientRegistration;
import com.mediflow.clinical.domain.model.OutpatientRegistration.Cancellation;
import com.mediflow.clinical.domain.model.OutpatientRegistration.Reservation;
import com.mediflow.clinical.domain.model.OutpatientRegistrationStatus;
import com.mediflow.clinical.domain.model.OutpatientReservationStatus;
import com.mediflow.clinical.domain.model.OutpatientVisitType;
import com.mediflow.clinical.domain.model.OutpatientPaymentOperationState;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.common.exception.DuplicateResourceException;

@Component
public class OutpatientRegistrationPersistenceAdapter implements OutpatientRegistrationRepositoryPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public OutpatientRegistrationPersistenceAdapter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public OutpatientRegistration insert(OutpatientRegistration registration, UUID actorAccountId,
            UUID actorStaffId, String action, Instant at) {
        try {
            Reservation reservation = registration.reservation();
            Cancellation cancellation = registration.cancellation();
            jdbc.update("""
                    INSERT INTO outpatient_registration(
                        registration_id, session_id, patient_id, revision, reception_mode, visit_type, status,
                        reservation_id, reservation_revision, reservation_status, reservation_expires_at,
                        checked_in_at, queue_number, next_fence_token, active_payment_operation_id, active_fence_token,
                        locked_at, reconcile_after, transaction_id, clearance_id, payment_completed_at,
                        cancellation_actor_account_id, cancellation_actor_staff_id, cancellation_reason, cancelled_at,
                        created_at, updated_at, previous_registration_id, follow_up_recommendation_id,
                        doctor_replacement_reason, patient_agreed_to_doctor_replacement, follow_up_reason)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, registration.registrationId(), registration.sessionId(), registration.patientId(),
                    registration.revision(), registration.receptionMode().name(), registration.visitType().name(),
                    registration.status().name(), reservation.reservationId(), reservation.revision(),
                    reservation.status().name(), timestamp(reservation.expiresAt()), timestamp(registration.checkedInAt()),
                    registration.queueNumber(), reservation.paymentOperationId(), reservation.fenceToken(),
                    timestamp(reservation.lockedAt()), timestamp(reservation.reconcileAfter()), reservation.transactionId(),
                    reservation.clearanceId(), timestamp(reservation.completedAt()), cancellation == null ? null : cancellation.actorAccountId(),
                    cancellation == null ? null : cancellation.actorStaffId(), cancellation == null ? null : cancellation.reason(),
                    cancellation == null ? null : timestamp(cancellation.cancelledAt()),
                    timestamp(registration.createdAt()), timestamp(registration.updatedAt()),
                    registration.followUp() == null ? null : registration.followUp().previousRegistrationId(),
                    registration.followUp() == null ? null : registration.followUp().recommendationId(),
                    registration.followUp() == null ? null : registration.followUp().replacementReason(),
                    registration.followUp() == null ? null : registration.followUp().patientAgreedToDoctorReplacement(),
                    registration.followUp() == null ? null : registration.followUp().reason());
        } catch (DuplicateKeyException collision) {
            throw new DuplicateResourceException("ACTIVE_REGISTRATION_EXISTS", "Patient already has an active registration for this session");
        }
        appendAudit(registration, null, actorAccountId, actorStaffId, action, at);
        return registration;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OutpatientRegistration> findById(UUID registrationId) {
        return query("SELECT * FROM outpatient_registration WHERE registration_id = ?", registrationId);
    }

    @Override
    @Transactional
    public Optional<OutpatientRegistration> findByIdForUpdate(UUID registrationId) {
        return query("SELECT * FROM outpatient_registration WHERE registration_id = ? FOR UPDATE", registrationId);
    }

    @Override
    @Transactional
    public Optional<OutpatientRegistration> findByReservationIdForUpdate(UUID reservationId) {
        return query("SELECT * FROM outpatient_registration WHERE reservation_id = ? FOR UPDATE", reservationId);
    }

    @Override
    @Transactional
    public void update(OutpatientRegistration before, OutpatientRegistration after, UUID actorAccountId,
            UUID actorStaffId, String action, Instant at) {
        if (!before.registrationId().equals(after.registrationId())) {
            throw new IllegalArgumentException("Registration identity cannot change");
        }
        Reservation reservation = after.reservation();
        Cancellation cancellation = after.cancellation();
        int changed = jdbc.update("""
                UPDATE outpatient_registration SET revision = ?, status = ?, reservation_revision = ?,
                    reservation_status = ?, reservation_expires_at = ?, checked_in_at = ?, queue_number = ?,
                    active_payment_operation_id = ?, active_fence_token = ?, locked_at = ?, reconcile_after = ?,
                    transaction_id = ?, clearance_id = ?, payment_completed_at = ?,
                    cancellation_actor_account_id = ?, cancellation_actor_staff_id = ?, cancellation_reason = ?,
                    cancelled_at = ?, updated_at = ?
                WHERE registration_id = ? AND revision = ? AND reservation_revision = ?
                """, after.revision(), after.status().name(), reservation.revision(), reservation.status().name(),
                timestamp(reservation.expiresAt()), timestamp(after.checkedInAt()), after.queueNumber(), reservation.paymentOperationId(),
                reservation.fenceToken(), timestamp(reservation.lockedAt()), timestamp(reservation.reconcileAfter()), reservation.transactionId(),
                reservation.clearanceId(), timestamp(reservation.completedAt()), cancellation == null ? null : cancellation.actorAccountId(),
                cancellation == null ? null : cancellation.actorStaffId(), cancellation == null ? null : cancellation.reason(),
                cancellation == null ? null : timestamp(cancellation.cancelledAt()), timestamp(after.updatedAt()), after.registrationId(),
                before.revision(), before.reservation().revision());
        if (changed != 1) throw new DuplicateResourceException("REVISION_CONFLICT", "Registration revision changed");
        appendAudit(after, before, actorAccountId, actorStaffId, action, at);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsActivePatientSession(UUID patientId, UUID sessionId) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM outpatient_registration WHERE patient_id = ? AND session_id = ?
                    AND status IN ('AWAITING_PAYMENT','CONFIRMED','IN_EXAM','RECONCILIATION_REQUIRED'))
                """, Boolean.class, patientId, sessionId);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<OutpatientRegistration> findByPatient(UUID patientId, PageQuery page) {
        long total = jdbc.queryForObject("SELECT count(*) FROM outpatient_registration WHERE patient_id = ?",
                Long.class, patientId);
        List<OutpatientRegistration> registrations = jdbc.query("""
                SELECT * FROM outpatient_registration WHERE patient_id = ?
                ORDER BY created_at DESC, registration_id LIMIT ? OFFSET ?
                """, this::mapOne, patientId, page.size(), (long) page.page() * page.size());
        return PageResult.of(registrations, total, page.page(), page.size());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<OutpatientRegistration> findBySession(UUID sessionId, PageQuery page) {
        long total = jdbc.queryForObject("SELECT count(*) FROM outpatient_registration WHERE session_id = ?",
                Long.class, sessionId);
        List<OutpatientRegistration> registrations = jdbc.query("""
                SELECT * FROM outpatient_registration WHERE session_id = ?
                ORDER BY created_at, registration_id LIMIT ? OFFSET ?
                """, this::mapOne, sessionId, page.size(), (long) page.page() * page.size());
        return PageResult.of(registrations, total, page.page(), page.size());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReservationPaymentContext> findPaymentContext(UUID reservationId) {
        return jdbc.query("""
                SELECT reservation_id, registration_id, patient_id, reservation_revision, reservation_status,
                    reservation_expires_at FROM outpatient_registration WHERE reservation_id = ?
                """, (rs, row) -> new ReservationPaymentContext(rs.getObject("reservation_id", UUID.class),
                rs.getObject("registration_id", UUID.class), rs.getObject("patient_id", UUID.class),
                rs.getInt("reservation_revision"), OutpatientReservationStatus.valueOf(rs.getString("reservation_status")),
                instant(rs, "reservation_expires_at")), reservationId).stream().findFirst();
    }

    @Override
    @Transactional
    public void lockPaymentOperation(UUID paymentOperationId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?::text, 1))", rs -> {
            rs.next();
            return null;
        }, paymentOperationId.toString());
    }

    @Override
    @Transactional
    public long allocateFenceToken(UUID registrationId) {
        Long token = jdbc.queryForObject("""
                UPDATE outpatient_registration SET next_fence_token = next_fence_token + 1
                WHERE registration_id = ? RETURNING next_fence_token
                """, Long.class, registrationId);
        if (token == null || token < 1) throw new IllegalStateException("payment fence could not be allocated");
        return token;
    }

    @Override
    @Transactional
    public int allocateQueueNumber(UUID sessionId) {
        Integer number = jdbc.queryForObject("""
                UPDATE consultation_session SET next_queue_number = next_queue_number + 1
                WHERE session_id = ? RETURNING next_queue_number - 1
                """, Integer.class, sessionId);
        if (number == null || number < 1) throw new IllegalStateException("queue number could not be allocated");
        return number;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentOperationReceipt> findPaymentOperation(UUID paymentOperationId) {
        return paymentOperationQuery("SELECT * FROM outpatient_payment_operation_receipt WHERE payment_operation_id = ?",
                paymentOperationId);
    }

    @Override
    @Transactional
    public Optional<PaymentOperationReceipt> findPaymentOperationForUpdate(UUID paymentOperationId) {
        return paymentOperationQuery("SELECT * FROM outpatient_payment_operation_receipt WHERE payment_operation_id = ? FOR UPDATE",
                paymentOperationId);
    }

    @Override
    @Transactional
    public PaymentOperationReceipt insertPaymentOperation(PaymentOperationReceipt receipt, Instant at) {
        try {
            jdbc.update("""
                    INSERT INTO outpatient_payment_operation_receipt(payment_operation_id, payment_request_id,
                        registration_id, reservation_id, patient_id, source_type, source_id, care_episode_id,
                        expected_reservation_revision, lease_reservation_revision, fence_token, state, locked_at,
                        reconcile_after, transaction_id, clearance_id, completed_at, close_reason, created_at, updated_at, basis, quote_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, receipt.paymentOperationId(), receipt.paymentRequestId(), receipt.registrationId(),
                    receipt.reservationId(), receipt.patientId(), receipt.sourceType(), receipt.sourceId(),
                    receipt.careEpisodeId(), receipt.expectedReservationRevision(), receipt.leaseReservationRevision(),
                    receipt.fenceToken(), receipt.state().name(), timestamp(receipt.lockedAt()), timestamp(receipt.reconcileAfter()),
                    receipt.transactionId(), receipt.clearanceId(), timestamp(receipt.completedAt()), receipt.closeReason(),
                    timestamp(at), timestamp(at), receipt.basis(), receipt.quoteId());
        } catch (DuplicateKeyException duplicate) {
            throw new DuplicateResourceException("IDEMPOTENCY_CONFLICT", "Payment operation id is already bound");
        }
        appendPaymentAudit(receipt, at);
        return receipt;
    }

    @Override
    @Transactional
    public void updatePaymentOperation(PaymentOperationReceipt before, PaymentOperationReceipt after, Instant at) {
        int updated = jdbc.update("""
                UPDATE outpatient_payment_operation_receipt SET state = ?, transaction_id = ?, clearance_id = ?,
                    completed_at = ?, close_reason = ?, updated_at = ?
                WHERE payment_operation_id = ? AND state = ? AND fence_token = ?
                """, after.state().name(), after.transactionId(), after.clearanceId(), timestamp(after.completedAt()),
                after.closeReason(), timestamp(at), after.paymentOperationId(), before.state().name(), before.fenceToken());
        if (updated != 1) throw new DuplicateResourceException("PAYMENT_OPERATION_CLOSED", "Payment operation is already terminal");
        appendPaymentAudit(after, at);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<com.mediflow.clinical.application.dto.response.FollowUpSourceSummary> findFollowUpSources(
            UUID patientId, PageQuery page) {
        String from = """
                FROM outpatient_registration r
                JOIN appointment a ON a.appointment_id = r.registration_id AND a.patient_id = r.patient_id
                JOIN consultation_session s ON s.session_id = r.session_id
                    AND s.doctor_id = a.doctor_id AND s.department_id = a.department_id
                JOIN medical_record m ON m.appointment_id = a.appointment_id AND m.patient_id = a.patient_id
                    AND m.doctor_id = a.doctor_id AND m.department_id = a.department_id
                WHERE r.patient_id = ? AND r.status = 'COMPLETED' AND a.status = 'COMPLETED'
                    AND a.care_contract_version = 2 AND m.status = 'COMPLETED' AND m.completed_at IS NOT NULL
                """;
        long total = jdbc.queryForObject("SELECT count(*) " + from, Long.class, patientId);
        var rows = jdbc.query("SELECT r.registration_id, m.record_id, r.patient_id, a.department_id, a.doctor_id, m.completed_at "
                + from + " ORDER BY m.completed_at DESC, r.registration_id LIMIT ? OFFSET ?",
                (rs, index) -> new com.mediflow.clinical.application.dto.response.FollowUpSourceSummary(
                        rs.getObject("registration_id", UUID.class), rs.getObject("record_id", UUID.class),
                        rs.getObject("patient_id", UUID.class), rs.getObject("department_id", UUID.class),
                        rs.getObject("doctor_id", UUID.class), instant(rs, "completed_at"), null),
                patientId, page.size(), (long) page.page() * page.size());
        return PageResult.of(rows, total, page.page(), page.size());
    }

    private Optional<OutpatientRegistration> query(String sql, UUID id) {
        return jdbc.query(sql, this::mapOne, id).stream().findFirst();
    }

    private Optional<PaymentOperationReceipt> paymentOperationQuery(String sql, UUID id) {
        return jdbc.query(sql, this::mapPaymentOperation, id).stream().findFirst();
    }

    private PaymentOperationReceipt mapPaymentOperation(ResultSet row, int index) throws SQLException {
        return new PaymentOperationReceipt(row.getObject("payment_operation_id", UUID.class),
                row.getObject("payment_request_id", UUID.class), row.getObject("registration_id", UUID.class),
                row.getObject("reservation_id", UUID.class), row.getObject("patient_id", UUID.class),
                row.getString("source_type"), row.getObject("source_id", UUID.class),
                row.getObject("care_episode_id", UUID.class), row.getInt("expected_reservation_revision"),
                (Integer) row.getObject("lease_reservation_revision"), nullableLong(row, "fence_token"),
                OutpatientPaymentOperationState.valueOf(row.getString("state")), instant(row, "locked_at"),
                instant(row, "reconcile_after"), row.getObject("transaction_id", UUID.class),
                row.getObject("clearance_id", UUID.class), instant(row, "completed_at"), row.getString("close_reason"), row.getString("basis"), row.getObject("quote_id", UUID.class));
    }

    private OutpatientRegistration mapOne(ResultSet row, int index) throws SQLException {
        UUID accountId = row.getObject("cancellation_actor_account_id", UUID.class);
        UUID staffId = row.getObject("cancellation_actor_staff_id", UUID.class);
        String reason = row.getString("cancellation_reason");
        Instant cancelledAt = instant(row, "cancelled_at");
        Cancellation cancellation = accountId == null ? null : new Cancellation(accountId, staffId, reason, cancelledAt);
        Reservation reservation = new Reservation(row.getObject("reservation_id", UUID.class),
                row.getInt("reservation_revision"), OutpatientReservationStatus.valueOf(row.getString("reservation_status")),
                instant(row, "reservation_expires_at"), row.getObject("active_payment_operation_id", UUID.class),
                nullableLong(row, "active_fence_token"), instant(row, "locked_at"), instant(row, "reconcile_after"),
                row.getObject("transaction_id", UUID.class), row.getObject("clearance_id", UUID.class),
                instant(row, "payment_completed_at"));
        return new OutpatientRegistration(row.getObject("registration_id", UUID.class), row.getInt("revision"),
                row.getObject("patient_id", UUID.class), row.getObject("session_id", UUID.class),
                OutpatientReceptionMode.valueOf(row.getString("reception_mode")),
                OutpatientVisitType.valueOf(row.getString("visit_type")),
                OutpatientRegistrationStatus.valueOf(row.getString("status")), instant(row, "checked_in_at"),
                (Integer) row.getObject("queue_number"), instant(row, "created_at"), instant(row, "updated_at"),
                reservation, cancellation, row.getObject("previous_registration_id") == null ? null :
                    new OutpatientRegistration.FollowUpLink(row.getObject("previous_registration_id", UUID.class),
                        row.getObject("follow_up_recommendation_id", UUID.class), row.getString("doctor_replacement_reason"),
                        (Boolean) row.getObject("patient_agreed_to_doctor_replacement"), row.getString("follow_up_reason")));
    }

    private void appendAudit(OutpatientRegistration after, OutpatientRegistration before,
            UUID actorAccountId, UUID actorStaffId, String action, Instant at) {
        try {
            jdbc.update("""
                    INSERT INTO outpatient_registration_audit(registration_id, registration_revision,
                        reservation_revision, action, actor_account_id, actor_staff_id, before_state, after_state, occurred_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
                    """, after.registrationId(), after.revision(), after.reservation().revision(), action,
                    actorAccountId, actorStaffId, before == null ? null : objectMapper.writeValueAsString(before),
                    objectMapper.writeValueAsString(after), timestamp(at));
        } catch (IOException serializationFailure) {
            throw new IllegalStateException("Outpatient registration audit could not be serialized", serializationFailure);
        }
    }

    private void appendPaymentAudit(PaymentOperationReceipt receipt, Instant at) {
        try {
            jdbc.update("""
                    INSERT INTO outpatient_payment_operation_audit(payment_operation_id, state, fence_token,
                        evidence, occurred_at) VALUES (?, ?, ?, ?::jsonb, ?)
                    """, receipt.paymentOperationId(), receipt.state().name(), receipt.fenceToken(),
                    objectMapper.writeValueAsString(receipt), timestamp(at));
        } catch (IOException serializationFailure) {
            throw new IllegalStateException("Payment operation audit could not be serialized", serializationFailure);
        }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).longValue();
    }
}
