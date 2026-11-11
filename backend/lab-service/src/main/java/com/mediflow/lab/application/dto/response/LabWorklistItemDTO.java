package com.mediflow.lab.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record LabWorklistItemDTO(UUID testId, UUID patientId, String labType, String testStatus, String serviceCode,
        String serviceName, String displayGroup, String specimenStatus, String reportStatus, Integer reportVersion,
        Instant dueAt, boolean overdue) {}
