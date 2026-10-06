package com.mediflow.surgery.application.dto.response;

import com.mediflow.surgery.domain.model.SurgeryStatus;
import java.time.Instant;
import java.util.UUID;

/** Bounded board projection; it never loads histories or exposes clinical narrative. */
public record SurgeryCaseBoardItem(UUID surgeryCaseId, UUID departmentId, String procedureCode,
        SurgeryStatus status, long revision, Instant requestedAt, UUID scheduleId,
        Instant plannedStartAt, Instant plannedEndAt) {}
