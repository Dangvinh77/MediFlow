package com.mediflow.inpatient.application.dto.response;

import com.mediflow.inpatient.domain.model.enums.ClinicalOrderType;
import com.mediflow.inpatient.domain.model.enums.ExternalOrderStatus;

import java.util.UUID;

public record ClinicalOrderReferenceDTO(
        UUID maThamChieu,
        UUID maDotNoiTru,
        ClinicalOrderType loaiYLenh,
        UUID maYLenhBenNgoai,
        ExternalOrderStatus status,
        String tomTat,
        Integer phienBanSuKien) {
}
