package com.mediflow.clinical.application.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mediflow.clinical.domain.model.ClinicQueueStage;

public record ClinicQueueEntryDTO(UUID registrationId, UUID patientId, UUID sessionId, UUID doctorId, UUID departmentId, UUID roomId,
        LocalDate queueDate, Integer queueNumber, UUID ticketId, Integer ticketRevision,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer roomQueueNumber, ClinicQueueStage stage, boolean priority, Instant checkedInAt,
        Instant calledAt, boolean vitalSignsRecorded, boolean readyForExam, boolean continuationRequired) {}
