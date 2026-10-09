package com.mediflow.report.application.mapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.domain.model.AdmissionReportFact;
import com.mediflow.report.domain.model.AdmissionReportFact.Kind;

/** Pure source-to-minimal-evidence mapping. Missing business revision is NOT defaulted to version 1. */
public class AdmissionReportFactMapper {
    public AdmissionReportFact map(DecodedCareFinanceEvent event) {
        var metadata = event.metadata();
        if (!"inpatient-service".equals(metadata.producer()) || metadata.version() != 1
                || !"admissionId".equals(metadata.sourceField())
                || !("admission.started".equals(metadata.eventType()) || "admission.closed".equals(metadata.eventType()))) {
            throw new IllegalArgumentException("Only canonical Inpatient start/administrative-close facts are supported");
        }
        var payload = event.payload();
        for (String field : java.util.List.of("sourceRevision", "revision", "supersedes", "supersedesId",
                "supersedesEventId", "originalEventId", "replacementOf", "correctionOf", "correctedAt")) {
            if (payload.containsKey(field)) throw new IllegalArgumentException("Admission corrections require a versioned source contract");
        }
        UUID admissionId = requiredUuid(payload, "admissionId");
        if (!metadata.sourceId().equals(admissionId)) throw new IllegalArgumentException("Admission source identity differs");
        UUID patientId = requiredUuid(payload, "patientId");
        if ("admission.started".equals(metadata.eventType())) {
            if (!(payload.get("emergency") instanceof Boolean emergency)) {
                throw new IllegalArgumentException("Explicit admission emergency boolean is required");
            }
            return new AdmissionReportFact(Kind.STARTED, admissionId, patientId,
                    requiredUuid(payload, "departmentId"), requiredUuid(payload, "bedId"),
                    Instant.parse(text(payload, "admittedAt")), emergency, optionalUuid(payload, "emergencyOverrideId"), null, null);
        }
        return new AdmissionReportFact(Kind.CLOSED, admissionId, patientId, null, null,
                Instant.parse(text(payload, "closedAt")), false, null,
                optionalUuid(payload, "settlementId"), optionalUuid(payload, "approvedOverrideId"));
    }

    private static String text(Map<String, Object> payload, String field) {
        if (!(payload.get(field) instanceof String value) || value.isBlank()) {
            throw new IllegalArgumentException("Explicit " + field + " is required");
        }
        return value;
    }
    private static UUID requiredUuid(Map<String, Object> payload, String field) {
        String value = text(payload, field);
        UUID id = UUID.fromString(value);
        if (!id.toString().equals(value)) throw new IllegalArgumentException("Canonical " + field + " is required");
        return id;
    }
    private static UUID optionalUuid(Map<String, Object> payload, String field) {
        return payload.get(field) == null ? null : requiredUuid(payload, field);
    }
}
