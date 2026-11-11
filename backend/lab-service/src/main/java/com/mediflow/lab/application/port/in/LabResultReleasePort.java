package com.mediflow.lab.application.port.in;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.mediflow.lab.application.event.LabResultV2Payload;
import com.mediflow.lab.domain.model.LabResult;

/** Completes a test from a released review: same clearance rules and event as the old result path. */
public interface LabResultReleasePort {
    void releaseReviewed(UUID testId, List<LabResult> results, String conclusion, LocalDate performedDate,
            UUID releasedBy, List<LabResultV2Payload.ReleasedValue> values, Instant at);
}
