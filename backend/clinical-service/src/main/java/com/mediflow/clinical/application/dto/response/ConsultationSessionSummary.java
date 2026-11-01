package com.mediflow.clinical.application.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Counts across the full requested department range; cancelled sessions contribute no seats. */
public record ConsultationSessionSummary(UUID departmentId, LocalDate fromDate, LocalDate toDate,
        long openSessionCount, long stoppedSessionCount, long cancelledSessionCount,
        long capacity, long heldCount, long paymentLockedCount, long confirmedCount,
        long consumedCount, long availableCount, Instant observedAt) {}
