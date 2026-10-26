package com.mediflow.clinical.domain.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/** Version-2 scheduled registration; the reservation state is the payment lock state. */
public record OutpatientRegistration(
        UUID registrationId,
        int revision,
        UUID patientId,
        UUID sessionId,
        OutpatientReceptionMode receptionMode,
        OutpatientVisitType visitType,
        OutpatientRegistrationStatus status,
        Instant checkedInAt,
        Integer queueNumber,
        Instant createdAt,
        Instant updatedAt,
        Reservation reservation,
        Cancellation cancellation,
        FollowUpLink followUp) {

    public OutpatientRegistration(UUID registrationId, int revision, UUID patientId, UUID sessionId,
            OutpatientReceptionMode receptionMode, OutpatientVisitType visitType, OutpatientRegistrationStatus status,
            Instant checkedInAt, Integer queueNumber, Instant createdAt, Instant updatedAt,
            Reservation reservation, Cancellation cancellation) {
        this(registrationId, revision, patientId, sessionId, receptionMode, visitType, status, checkedInAt,
                queueNumber, createdAt, updatedAt, reservation, cancellation, null);
    }

    public OutpatientRegistration asFollowUp(FollowUpLink link) {
        Objects.requireNonNull(link, "follow-up source is required");
        return new OutpatientRegistration(registrationId, revision, patientId, sessionId, receptionMode,
                OutpatientVisitType.FOLLOW_UP, status, checkedInAt, queueNumber, createdAt, updatedAt,
                reservation, cancellation, link);
    }

    public OutpatientRegistration {
        Objects.requireNonNull(registrationId, "registration id is required");
        Objects.requireNonNull(patientId, "patient id is required");
        Objects.requireNonNull(sessionId, "session id is required");
        Objects.requireNonNull(receptionMode, "reception mode is required");
        Objects.requireNonNull(visitType, "visit type is required");
        Objects.requireNonNull(status, "registration status is required");
        Objects.requireNonNull(createdAt, "created at is required");
        Objects.requireNonNull(updatedAt, "updated at is required");
        Objects.requireNonNull(reservation, "reservation is required");
        if (revision < 1) throw new IllegalArgumentException("registration revision must be positive");
        if ((status == OutpatientRegistrationStatus.CONFIRMED || status == OutpatientRegistrationStatus.IN_EXAM
                || status == OutpatientRegistrationStatus.COMPLETED) && queueNumber == null) {
            throw new IllegalArgumentException("confirmed registrations require a queue number");
        }
        if (status == OutpatientRegistrationStatus.CANCELLED && cancellation == null) {
            throw new IllegalArgumentException("cancelled registrations require cancellation audit");
        }
        if (status != OutpatientRegistrationStatus.CANCELLED && cancellation != null) {
            throw new IllegalArgumentException("cancellation audit only belongs to a cancelled registration");
        }
    }

    public static OutpatientRegistration create(
            UUID registrationId,
            UUID reservationId,
            UUID patientId,
            ConsultationSession session,
            OutpatientReceptionMode receptionMode,
            Instant now) {
        Objects.requireNonNull(session, "session is required");
        Objects.requireNonNull(now, "registration time is required");
        ConsultationSession.Configuration config = session.configuration();
        Instant expiresAt = now.plus(15, ChronoUnit.MINUTES);
        Reservation reservation = new Reservation(reservationId, 1, OutpatientReservationStatus.HELD,
                expiresAt, null, null, null, null, null, null, null);
        return new OutpatientRegistration(registrationId, 1, patientId, session.sessionId(), receptionMode,
                OutpatientVisitType.NEW_VISIT, OutpatientRegistrationStatus.AWAITING_PAYMENT,
                receptionMode == OutpatientReceptionMode.AT_COUNTER ? now : null, null, now, now, reservation, null);
    }

    public OutpatientRegistration arrive(int expectedRevision, Instant at) {
        requireRevision(expectedRevision);
        if ((status != OutpatientRegistrationStatus.AWAITING_PAYMENT
                && status != OutpatientRegistrationStatus.CONFIRMED)
                || checkedInAt != null || at == null) {
            throw new IllegalStateException("arrival cannot be recorded in the current registration state");
        }
        return copy(revision + 1, status, at, queueNumber, updatedAt(at), reservation, cancellation);
    }

    public OutpatientRegistration acquirePayment(
            int expectedReservationRevision, UUID paymentOperationId, long fenceToken, Instant lockedAt) {
        if (expectedReservationRevision != reservation.revision()) {
            throw new IllegalStateException("REVISION_CONFLICT");
        }
        if (reservation.status() != OutpatientReservationStatus.HELD
                || !lockedAt.isBefore(reservation.expiresAt())) {
            throw new IllegalStateException("RESERVATION_NOT_PAYABLE");
        }
        if (paymentOperationId == null || fenceToken < 1) {
            throw new IllegalArgumentException("payment operation and positive fence token are required");
        }
        Reservation locked = new Reservation(reservation.reservationId(), reservation.revision() + 1,
                OutpatientReservationStatus.PAYMENT_LOCKED, reservation.expiresAt(), paymentOperationId, fenceToken,
                lockedAt, lockedAt.plus(2, ChronoUnit.MINUTES), null, null, null);
        return copy(revision + 1, status, checkedInAt, queueNumber, lockedAt, locked, cancellation);
    }

    public OutpatientRegistration resolveCompleted(
            UUID paymentOperationId,
            long fenceToken,
            int queueNumber,
            UUID transactionId,
            UUID clearanceId,
            Instant completedAt,
            boolean sessionEnded) { return resolveCompleted(paymentOperationId, fenceToken, queueNumber, transactionId, clearanceId, completedAt, sessionEnded, "PATIENT_PAYMENT"); }

    public OutpatientRegistration resolveCompleted(
            UUID paymentOperationId,
            long fenceToken,
            int queueNumber,
            UUID transactionId,
            UUID clearanceId,
            Instant completedAt,
            boolean sessionEnded, String basis) {
        if (reservation.status() == OutpatientReservationStatus.CONFIRMED
                && Objects.equals(reservation.paymentOperationId(), paymentOperationId)
                && Objects.equals(reservation.fenceToken(), fenceToken)
                && Objects.equals(reservation.transactionId(), transactionId)
                && Objects.equals(reservation.clearanceId(), clearanceId)
                && Objects.equals(reservation.completedAt(), completedAt)) return this;
        if (reservation.status() == OutpatientReservationStatus.RECONCILIATION_REQUIRED
                && Objects.equals(reservation.paymentOperationId(), paymentOperationId)
                && Objects.equals(reservation.fenceToken(), fenceToken)
                && Objects.equals(reservation.transactionId(), transactionId)
                && Objects.equals(reservation.clearanceId(), clearanceId)) return this;
        requireLockedPayment(paymentOperationId, fenceToken);
        if ((!sessionEnded && queueNumber < 1) || ("ZERO_LIABILITY".equals(basis) ? transactionId != null : transactionId == null) || clearanceId == null || completedAt == null) {
            throw new IllegalArgumentException("payment receipt is invalid");
        }
        boolean late = sessionEnded;
        OutpatientReservationStatus reservationStatus = late
                ? OutpatientReservationStatus.RECONCILIATION_REQUIRED : OutpatientReservationStatus.CONFIRMED;
        OutpatientRegistrationStatus registrationStatus = late
                ? OutpatientRegistrationStatus.RECONCILIATION_REQUIRED : OutpatientRegistrationStatus.CONFIRMED;
        Reservation resolved = new Reservation(reservation.reservationId(), reservation.revision() + 1,
                reservationStatus, reservation.expiresAt(), paymentOperationId, fenceToken,
                reservation.lockedAt(), reservation.reconcileAfter(), transactionId, clearanceId, completedAt);
        Integer assignedQueueNumber = late ? null : queueNumber;
        return copy(revision + 1, registrationStatus, checkedInAt, assignedQueueNumber, completedAt,
                resolved, cancellation);
    }

    public OutpatientRegistration resolveClosedNotPaid(
            UUID paymentOperationId, long fenceToken, Instant resolvedAt, boolean sessionEnded) {
        if (reservation.status() == OutpatientReservationStatus.HELD
                || reservation.status() == OutpatientReservationStatus.EXPIRED) return this;
        requireLockedPayment(paymentOperationId, fenceToken);
        if (resolvedAt == null) throw new IllegalArgumentException("payment resolution time is required");
        boolean expired = sessionEnded || !resolvedAt.isBefore(reservation.expiresAt());
        OutpatientReservationStatus reservationStatus = expired
                ? OutpatientReservationStatus.EXPIRED : OutpatientReservationStatus.HELD;
        OutpatientRegistrationStatus registrationStatus = expired
                ? OutpatientRegistrationStatus.EXPIRED : OutpatientRegistrationStatus.AWAITING_PAYMENT;
        Reservation resolved = new Reservation(reservation.reservationId(), reservation.revision() + 1,
                reservationStatus, reservation.expiresAt(), paymentOperationId, fenceToken,
                reservation.lockedAt(), reservation.reconcileAfter(), null, null, null);
        return copy(revision + 1, registrationStatus, checkedInAt, queueNumber, resolvedAt, resolved, cancellation);
    }

    public OutpatientRegistration cancel(int expectedRevision, UUID actorAccountId, UUID actorStaffId,
            String reason, Instant cancelledAt) {
        requireRevision(expectedRevision);
        if ((status != OutpatientRegistrationStatus.AWAITING_PAYMENT
                && status != OutpatientRegistrationStatus.CONFIRMED)
                || (reservation.status() != OutpatientReservationStatus.HELD
                && reservation.status() != OutpatientReservationStatus.CONFIRMED)) {
            throw new IllegalStateException("PAYMENT_OPERATION_IN_PROGRESS");
        }
        if (actorAccountId == null || reason == null || reason.isBlank() || reason.length() > 500
                || cancelledAt == null || cancelledAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("cancellation audit is incomplete");
        }
        Cancellation audit = new Cancellation(actorAccountId, actorStaffId, reason, cancelledAt);
        Reservation cancelled = new Reservation(reservation.reservationId(), reservation.revision() + 1,
                OutpatientReservationStatus.CANCELLED, reservation.expiresAt(), reservation.paymentOperationId(),
                reservation.fenceToken(), reservation.lockedAt(), reservation.reconcileAfter(),
                reservation.transactionId(), reservation.clearanceId(), reservation.completedAt());
        return copy(revision + 1, OutpatientRegistrationStatus.CANCELLED, checkedInAt, queueNumber,
                cancelledAt, cancelled, audit);
    }

    public OutpatientRegistration expire(Instant expiredAt) {
        if (reservation.status() != OutpatientReservationStatus.HELD
                || expiredAt == null || expiredAt.isBefore(reservation.expiresAt())) return this;
        Reservation expired = new Reservation(reservation.reservationId(), reservation.revision() + 1,
                OutpatientReservationStatus.EXPIRED, reservation.expiresAt(), reservation.paymentOperationId(),
                reservation.fenceToken(), reservation.lockedAt(), reservation.reconcileAfter(),
                reservation.transactionId(), reservation.clearanceId(), reservation.completedAt());
        return copy(revision + 1, OutpatientRegistrationStatus.EXPIRED, checkedInAt, queueNumber,
                expiredAt, expired, cancellation);
    }

    public OutpatientRegistration startExam(Instant startedAt) {
        if (status != OutpatientRegistrationStatus.CONFIRMED
                || checkedInAt == null || reservation.status() != OutpatientReservationStatus.CONFIRMED
                || startedAt == null) {
            throw new IllegalStateException("CLINICAL_EXAM_CLEARANCE_REQUIRED");
        }
        Reservation consumed = new Reservation(reservation.reservationId(), reservation.revision() + 1,
                OutpatientReservationStatus.CONSUMED, reservation.expiresAt(), reservation.paymentOperationId(),
                reservation.fenceToken(), reservation.lockedAt(), reservation.reconcileAfter(),
                reservation.transactionId(), reservation.clearanceId(), reservation.completedAt());
        return copy(revision + 1, OutpatientRegistrationStatus.IN_EXAM, checkedInAt, queueNumber,
                startedAt, consumed, cancellation);
    }

    public OutpatientRegistration complete(Instant completedAt) {
        if (status != OutpatientRegistrationStatus.IN_EXAM || completedAt == null) {
            throw new IllegalStateException("CLINICAL_INVALID_STATUS_TRANSITION");
        }
        return copy(revision + 1, OutpatientRegistrationStatus.COMPLETED, checkedInAt, queueNumber,
                completedAt, reservation, cancellation);
    }

    private void requireLockedPayment(UUID paymentOperationId, long fenceToken) {
        if (status != OutpatientRegistrationStatus.AWAITING_PAYMENT
                || reservation.status() != OutpatientReservationStatus.PAYMENT_LOCKED
                || !Objects.equals(reservation.paymentOperationId(), paymentOperationId)
                || !Objects.equals(reservation.fenceToken(), fenceToken)) {
            throw new IllegalStateException("PAYMENT_OPERATION_CLOSED");
        }
    }

    private void requireRevision(int expectedRevision) {
        if (expectedRevision != revision) throw new IllegalStateException("REVISION_CONFLICT");
    }

    private static Instant updatedAt(Instant value) {
        return value;
    }

    private OutpatientRegistration copy(int nextRevision, OutpatientRegistrationStatus nextStatus,
            Instant nextCheckedInAt, Integer nextQueueNumber, Instant nextUpdatedAt, Reservation nextReservation,
            Cancellation nextCancellation) {
        return new OutpatientRegistration(registrationId, nextRevision, patientId, sessionId, receptionMode,
                visitType, nextStatus, nextCheckedInAt, nextQueueNumber, createdAt, nextUpdatedAt,
                nextReservation, nextCancellation, followUp);
    }

    public record FollowUpLink(UUID previousRegistrationId, UUID recommendationId,
            String replacementReason, Boolean patientAgreedToDoctorReplacement, String reason) {
        public FollowUpLink(UUID previousRegistrationId, UUID recommendationId,
                String replacementReason, Boolean patientAgreedToDoctorReplacement) {
            this(previousRegistrationId, recommendationId, replacementReason, patientAgreedToDoctorReplacement, null);
        }
        public FollowUpLink {
            Objects.requireNonNull(previousRegistrationId, "previous registration is required");
            if (reason != null && reason.length() > 1000) throw new IllegalArgumentException("follow-up reason is too long");
            if (replacementReason != null && (replacementReason.isBlank() || replacementReason.length() > 1000
                    || !Boolean.TRUE.equals(patientAgreedToDoctorReplacement))) {
                throw new IllegalArgumentException("doctor replacement requires reason and patient consent");
            }
            if (replacementReason == null && patientAgreedToDoctorReplacement != null) {
                throw new IllegalArgumentException("consent requires doctor replacement");
            }
        }
    }

    public record Reservation(UUID reservationId, int revision, OutpatientReservationStatus status,
            Instant expiresAt, UUID paymentOperationId, Long fenceToken, Instant lockedAt, Instant reconcileAfter,
            UUID transactionId, UUID clearanceId, Instant completedAt) {
        public Reservation {
            Objects.requireNonNull(reservationId, "reservation id is required");
            Objects.requireNonNull(status, "reservation status is required");
            Objects.requireNonNull(expiresAt, "reservation expiry is required");
            if (revision < 1) throw new IllegalArgumentException("reservation revision must be positive");
            if (status == OutpatientReservationStatus.PAYMENT_LOCKED
                    && (paymentOperationId == null || fenceToken == null || lockedAt == null || reconcileAfter == null)) {
                throw new IllegalArgumentException("payment-locked reservations require a durable lease");
            }
        }
    }

    public record Cancellation(UUID actorAccountId, UUID actorStaffId, String reason, Instant cancelledAt) {
        public Cancellation {
            Objects.requireNonNull(actorAccountId, "cancellation actor account is required");
            if (reason == null || reason.isBlank() || reason.length() > 500) {
                throw new IllegalArgumentException("cancellation reason must contain 1 to 500 characters");
            }
            Objects.requireNonNull(cancelledAt, "cancellation time is required");
        }
    }
}
