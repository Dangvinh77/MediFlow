package com.mediflow.billing.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record FinancialClearanceLookupDTO(boolean exists, UUID clearanceId, boolean eligible, UUID invoiceId,
        UUID accountId, UUID patientId, String purpose, String careEpisodeType, UUID careEpisodeId,
        UUID admissionId, UUID surgeryCaseId, Instant grantedAt, Instant expiresAt, Instant observedAt) {}
