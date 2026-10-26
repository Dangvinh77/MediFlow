package com.mediflow.clinical.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.clinical.application.dto.response.ReservationPaymentContext;
import com.mediflow.clinical.domain.model.OutpatientRegistration;
import com.mediflow.clinical.domain.model.OutpatientPaymentOperationState;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;

public interface OutpatientRegistrationRepositoryPort {
    OutpatientRegistration insert(OutpatientRegistration registration, UUID actorAccountId, UUID actorStaffId,
            String action, Instant at);
    Optional<OutpatientRegistration> findById(UUID registrationId);
    Optional<OutpatientRegistration> findByIdForUpdate(UUID registrationId);
    Optional<OutpatientRegistration> findByReservationIdForUpdate(UUID reservationId);
    void update(OutpatientRegistration before, OutpatientRegistration after, UUID actorAccountId, UUID actorStaffId,
            String action, Instant at);
    boolean existsActivePatientSession(UUID patientId, UUID sessionId);
    PageResult<OutpatientRegistration> findByPatient(UUID patientId, PageQuery page);
    PageResult<OutpatientRegistration> findBySession(UUID sessionId, PageQuery page);
    PageResult<com.mediflow.clinical.application.dto.response.FollowUpSourceSummary> findFollowUpSources(UUID patientId, PageQuery page);
    Optional<ReservationPaymentContext> findPaymentContext(UUID reservationId);
    void lockPaymentOperation(UUID paymentOperationId);
    long allocateFenceToken(UUID registrationId);
    int allocateQueueNumber(UUID sessionId);
    Optional<PaymentOperationReceipt> findPaymentOperation(UUID paymentOperationId);
    Optional<PaymentOperationReceipt> findPaymentOperationForUpdate(UUID paymentOperationId);
    PaymentOperationReceipt insertPaymentOperation(PaymentOperationReceipt receipt, Instant at);
    void updatePaymentOperation(PaymentOperationReceipt before, PaymentOperationReceipt after, Instant at);

    record PaymentOperationReceipt(UUID paymentOperationId, UUID paymentRequestId, UUID registrationId,
            UUID reservationId, UUID patientId, String sourceType, UUID sourceId, UUID careEpisodeId,
            int expectedReservationRevision, Integer leaseReservationRevision, Long fenceToken,
            OutpatientPaymentOperationState state, Instant lockedAt, Instant reconcileAfter,
            UUID transactionId, UUID clearanceId, Instant completedAt, String closeReason, String basis, UUID quoteId) {
    public PaymentOperationReceipt(UUID paymentOperationId, UUID paymentRequestId, UUID registrationId,
            UUID reservationId, UUID patientId, String sourceType, UUID sourceId, UUID careEpisodeId,
            int expectedReservationRevision, Integer leaseReservationRevision, Long fenceToken,
            OutpatientPaymentOperationState state, Instant lockedAt, Instant reconcileAfter,
            UUID transactionId, UUID clearanceId, Instant completedAt, String closeReason) { this(paymentOperationId, paymentRequestId, registrationId, reservationId, patientId, sourceType, sourceId, careEpisodeId, expectedReservationRevision, leaseReservationRevision, fenceToken, state, lockedAt, reconcileAfter, transactionId, clearanceId, completedAt, closeReason, "PATIENT_PAYMENT", null); }
}
}
