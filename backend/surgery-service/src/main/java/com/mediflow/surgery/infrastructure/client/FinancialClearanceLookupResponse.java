package com.mediflow.surgery.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FinancialClearanceLookupResponse(Boolean exists, UUID clearanceId, Boolean eligible, UUID invoiceId,
        UUID accountId, UUID patientId, String purpose, String careEpisodeType, UUID careEpisodeId,
        UUID admissionId, UUID surgeryCaseId, Instant grantedAt, Instant expiresAt, Instant observedAt) {}
