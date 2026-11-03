package com.mediflow.report.application.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A currently readable accepted range, not a grant or a promise of future availability. */
public record OutpatientAvailablePeriodDTO(UUID coverageId, LocalDate fromDate, LocalDate toDate,
        UUID departmentId, String currency, String zoneId, Instant sourceObservedAt,
        Instant reconciledAt, boolean syntheticData) { }
