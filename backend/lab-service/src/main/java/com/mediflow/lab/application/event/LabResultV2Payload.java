package com.mediflow.lab.application.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mediflow.lab.domain.model.CareEpisodeType;

/** Verified result snapshot for a V2 Lab test. */
public record LabResultV2Payload(
        UUID labId,
        UUID patientId,
        UUID recordId,
        UUID departmentId,
        CareEpisodeType careEpisodeType,
        UUID careEpisodeId,
        String labType,
        int resultVersion,
        List<LabResultCreatedEvent.Result> results,
        String conclusion,
        UUID verifiedBy,
        LocalDate performedDate,
        Instant completedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<ReleasedValue> values,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID releasedBy
) {
    public LabResultV2Payload {
        results = results == null ? null : List.copyOf(results);
        values = values == null ? null : List.copyOf(values);
    }

    /** Result entered through the old endpoint: no structured values, no second-person release. */
    public LabResultV2Payload(UUID labId, UUID patientId, UUID recordId, UUID departmentId,
            CareEpisodeType careEpisodeType, UUID careEpisodeId, String labType, int resultVersion,
            List<LabResultCreatedEvent.Result> results, String conclusion, UUID verifiedBy, LocalDate performedDate,
            Instant completedAt) {
        this(labId, patientId, recordId, departmentId, careEpisodeType, careEpisodeId, labType, resultVersion,
                results, conclusion, verifiedBy, performedDate, completedAt, null, null);
    }

    /** One template value as released: the flag is the Lab's interpretation at release time. */
    public record ReleasedValue(String fieldCode, String label, String operator, String value, String unit,
            String noResultReason, String flag, String referenceRange) {
    }
}
