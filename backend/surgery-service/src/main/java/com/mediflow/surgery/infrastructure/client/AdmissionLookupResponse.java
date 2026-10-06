package com.mediflow.surgery.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown=true)
public record AdmissionLookupResponse(Boolean exists,UUID admissionId,UUID patientId,UUID departmentId,
        UUID sourceRecordId,String status,Boolean eligible,String sourceRevision,Instant observedAt) {}
