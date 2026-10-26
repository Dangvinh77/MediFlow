package com.mediflow.clinical.application.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentLockCommand(@NotNull UUID paymentOperationId, @NotNull UUID paymentRequestId,
        @NotNull UUID registrationId, @NotNull UUID patientId, @NotNull String sourceType,
        @NotNull UUID sourceId, @NotNull UUID careEpisodeId, @Positive int expectedReservationRevision, String basis, UUID quoteId) {
    public PaymentLockCommand(@NotNull UUID paymentOperationId, @NotNull UUID paymentRequestId,
        @NotNull UUID registrationId, @NotNull UUID patientId, @NotNull String sourceType,
        @NotNull UUID sourceId, @NotNull UUID careEpisodeId, @Positive int expectedReservationRevision) { this(paymentOperationId, paymentRequestId, registrationId, patientId, sourceType, sourceId, careEpisodeId, expectedReservationRevision, "PATIENT_PAYMENT", null); }
}
