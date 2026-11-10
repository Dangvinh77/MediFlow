package com.mediflow.clinical.application.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.mediflow.clinical.domain.model.ClinicQueueTicketStatus;

public record ClinicQueueTicketDTO(UUID ticketId, int revision, UUID registrationId, UUID roomId, LocalDate queueDate,
        int roomQueueNumber, ClinicQueueTicketStatus status, boolean priority, Instant issuedAt, Instant calledAt) {}
