package com.mediflow.lab.application.dto.context;

import com.mediflow.lab.domain.model.CareEpisodeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Persisted Clinical authority for one Lab source order. */
public record LabClinicalOrderContext(
        UUID sourceOrderId,
        UUID recordId,
        UUID patientId,
        UUID requestingDoctorId,
        UUID requestingDepartmentId,
        CareEpisodeType careEpisodeType,
        UUID careEpisodeId,
        String labType,
        LocalDate requestedDate,
        String priceCode,
        Instant orderedAt,
        Instant observedAt) {}
