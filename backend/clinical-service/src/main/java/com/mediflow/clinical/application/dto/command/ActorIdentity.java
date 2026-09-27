package com.mediflow.clinical.application.dto.command;

import java.util.UUID;

public record ActorIdentity(UUID staffId, String role) {
}
