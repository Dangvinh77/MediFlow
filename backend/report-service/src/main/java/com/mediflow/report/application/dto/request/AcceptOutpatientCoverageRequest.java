package com.mediflow.report.application.dto.request;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** ADMIN attests that referenced immutable source exports were reviewed and reconciled. */
public record AcceptOutpatientCoverageRequest(UUID requestId, LocalDate fromDate, LocalDate toDate,
        UUID departmentId, String zoneId, String currency, Map<String, SourceProof> sources,
        Instant sourceObservedAt, Instant reconciledAt, String acceptanceReference,
        String expectedFingerprint) {
    public AcceptOutpatientCoverageRequest {
        if (sources != null) sources = java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(sources));
    }
    public record SourceProof(String reference, String exportSha256,
            @com.fasterxml.jackson.annotation.JsonProperty(value="rowCount", required=true)
            @com.fasterxml.jackson.annotation.JsonSetter(nulls=com.fasterxml.jackson.annotation.Nulls.FAIL) long rowCount,
            String localFingerprint) { }
}
