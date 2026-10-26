package com.mediflow.clinical.domain.model;

import java.time.LocalTime;
import java.util.Objects;

/** A half-open interval during which a consultation session does not accept registrations. */
public record ConsultationBreak(LocalTime startTime, LocalTime endTime) {
    public ConsultationBreak {
        Objects.requireNonNull(startTime, "break start time is required");
        Objects.requireNonNull(endTime, "break end time is required");
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("break start time must be before end time");
        }
    }

    public boolean overlaps(ConsultationBreak other) {
        return startTime.isBefore(other.endTime) && other.startTime.isBefore(endTime);
    }
}
