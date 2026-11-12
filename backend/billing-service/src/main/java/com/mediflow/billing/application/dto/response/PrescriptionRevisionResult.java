package com.mediflow.billing.application.dto.response;

import java.util.UUID;

public record PrescriptionRevisionResult(UUID prescriptionId, UUID operationId, long revision,
        String status, String payloadFingerprint) { }
