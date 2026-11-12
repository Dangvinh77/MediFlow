package com.mediflow.clinical.application.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Where an unfinished examination stands; deliberately carries no clinical content. */
public record OpenVisitDTO(UUID registrationId, UUID recordId, UUID doctorId, UUID departmentId, LocalDate sessionDate, Instant startedAt) {
}
