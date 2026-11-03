package com.mediflow.organization.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record DemoMailboxMessageDetailDTO(UUID messageId, String subject, String from, String to,
        Instant createdAt, String textBody, String htmlBody) { }
