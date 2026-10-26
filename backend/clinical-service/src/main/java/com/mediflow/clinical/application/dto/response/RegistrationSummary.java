package com.mediflow.clinical.application.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.clinical.domain.model.OutpatientRegistrationStatus;
import com.mediflow.clinical.domain.model.OutpatientVisitType;

public record RegistrationSummary(UUID registrationId, UUID patientId, OutpatientVisitType visitType,
        OutpatientRegistrationStatus status, Instant checkedInAt, Integer queueNumber, boolean readyForExam) {}
