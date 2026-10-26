package com.mediflow.lab.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record LabOrderLifecycleDTO(UUID sourceOrderId, boolean exists, UUID testId, UUID recordId,
        UUID patientId, UUID departmentId, String careEpisodeType, UUID careEpisodeId, String status, Instant observedAt) {}
