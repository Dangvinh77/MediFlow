package com.mediflow.report.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record OperationalPublicationDTO(UUID commandId, UUID generationId, UUID previousGenerationId,
        Instant publishedAt, String action) { }
