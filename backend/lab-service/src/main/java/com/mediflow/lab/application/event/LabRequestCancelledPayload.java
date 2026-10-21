package com.mediflow.lab.application.event;

import java.time.Instant;
import java.util.UUID;
import com.mediflow.lab.domain.model.CareEpisodeType;

/** Exact Lab source identity and immutable cancellation audit, version 1. */
public record LabRequestCancelledPayload(UUID testId, UUID patientId, UUID recordId,
        UUID requestingDepartmentId, CareEpisodeType careEpisodeType, UUID careEpisodeId,
        UUID sourceOrderId, String sourceType, UUID sourceId, String priceCode, String labType,
        UUID cancelledBy, String reason, Instant cancelledAt) {}
