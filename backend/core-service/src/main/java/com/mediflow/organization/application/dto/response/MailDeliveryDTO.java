package com.mediflow.organization.application.dto.response;

import java.util.UUID;

public record MailDeliveryDTO(UUID messageId, Status status) {
    public enum Status { PENDING, SENT, FAILED }
}
