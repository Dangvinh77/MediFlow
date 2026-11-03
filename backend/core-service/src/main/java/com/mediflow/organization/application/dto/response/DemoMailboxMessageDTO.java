package com.mediflow.organization.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record DemoMailboxMessageDTO(UUID messageId, String subject, String from, String to,
        Instant createdAt, String preview) { }
