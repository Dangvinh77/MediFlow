package com.mediflow.clinical.application.dto.response;

import java.util.UUID;

public record DiagnosisDTO(
        UUID diagnosisId, String diagnosisName, String description, String icdCode
) {}
