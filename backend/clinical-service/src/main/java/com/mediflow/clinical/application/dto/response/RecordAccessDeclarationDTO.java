package com.mediflow.clinical.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record RecordAccessDeclarationDTO(UUID declarationId, UUID patientId, String purpose, Instant expiresAt) {
}
