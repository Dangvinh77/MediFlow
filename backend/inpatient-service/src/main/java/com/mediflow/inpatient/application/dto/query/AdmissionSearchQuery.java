package com.mediflow.inpatient.application.dto.query;

import com.mediflow.common.api.PageQuery;
import com.mediflow.inpatient.domain.model.enums.AdmissionStatus;

import java.time.LocalDate;
import java.util.UUID;

public record AdmissionSearchQuery(
        UUID maKhoa,
        UUID maBenhNhan,
        AdmissionStatus status,
        LocalDate tuNgay,
        LocalDate denNgay,
        PageQuery phanTrang) {
}
