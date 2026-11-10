package com.mediflow.clinical.application.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.mediflow.clinical.domain.model.OutpatientRegistrationStatus;
import com.mediflow.clinical.domain.model.OutpatientReservationStatus;

public record ScheduleImpactDTO(List<UUID> sessionIds, int heldCount, int paymentLockedCount, int confirmedCount,
        int consumedCount, List<AffectedRegistration> registrations) {
    public record AffectedRegistration(UUID registrationId, UUID patientId, UUID sessionId,
            OutpatientRegistrationStatus status, OutpatientReservationStatus reservationStatus, Instant checkedInAt,
            Integer queueNumber, Integer roomQueueNumber) {}
}
