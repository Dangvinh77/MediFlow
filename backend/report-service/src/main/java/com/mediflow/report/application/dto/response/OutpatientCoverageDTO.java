package com.mediflow.report.application.dto.response;

import com.mediflow.report.domain.model.OutpatientCoverage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** Immutable approval receipt; covered reads must still validate the current evidence witness. */
public record OutpatientCoverageDTO(UUID coverageId, LocalDate from, LocalDate to, UUID departmentId,
        String zoneId, String currency, Map<String,String> sourceReferences, Instant sourceObservedAt,
        Instant reconciledAt, Instant acceptedAt, UUID acceptedBy, String acceptanceReference, String evidenceFingerprint) {
    public static OutpatientCoverageDTO from(OutpatientCoverage value) {
        return new OutpatientCoverageDTO(value.coverageId(),value.from(),value.to(),value.departmentId(),value.zoneId(),
                value.currency(),value.sourceReferences(),value.sourceObservedAt(),value.reconciledAt(),value.acceptedAt(),
                value.acceptedBy(),value.acceptanceReference(),value.evidenceFingerprint());
    }
    public OutpatientCoverage toModel() {
        return new OutpatientCoverage(coverageId,from,to,departmentId,zoneId,currency,sourceReferences,sourceObservedAt,
                reconciledAt,acceptedAt,acceptedBy,acceptanceReference,evidenceFingerprint);
    }
}
