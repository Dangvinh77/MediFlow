package com.mediflow.clinical.application.dto.response;

import java.time.LocalDate;
import java.util.UUID;

/** A finished visit reception may link a return visit to; deliberately carries no clinical content. */
public record ReturnSourceDTO(UUID recordId, String status, LocalDate examinationDate, UUID departmentId) {
}
