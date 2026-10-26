package com.mediflow.clinical.application.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.clinical.domain.model.OutpatientReservationStatus;

public record ReservationPaymentContext(UUID reservationId, UUID registrationId, UUID patientId,
        int reservationRevision, OutpatientReservationStatus reservationStatus, Instant expiresAt) {}
