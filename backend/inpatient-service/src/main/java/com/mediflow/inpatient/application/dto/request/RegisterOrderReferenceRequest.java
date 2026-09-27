package com.mediflow.inpatient.application.dto.request;

import com.mediflow.inpatient.domain.model.enums.ClinicalOrderType;
import com.mediflow.inpatient.domain.model.enums.ExternalOrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RegisterOrderReferenceRequest(
        @NotNull ClinicalOrderType loaiYLenh,
        @NotNull UUID maYLenhBenNgoai,
        @NotNull ExternalOrderStatus status,
        @Size(max = 2000) String tomTat) {
}
