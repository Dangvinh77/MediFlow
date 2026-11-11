package com.mediflow.clinical.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ConsultationNoteDTO(UUID noteId, UUID recordId, UUID authorStaffId, String opinion, Instant createdAt) {
}
