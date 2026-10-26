package com.mediflow.clinical.application.dto.command;

import java.util.UUID;

public record ActorIdentity(UUID accountId, UUID staffId, UUID patientId, String role) {
    public ActorIdentity(UUID staffId, String role) {
        this(null, staffId, null, role);
    }
}
