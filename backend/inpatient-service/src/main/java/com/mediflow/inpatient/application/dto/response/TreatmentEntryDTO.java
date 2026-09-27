package com.mediflow.inpatient.application.dto.response;

import com.mediflow.inpatient.domain.model.enums.TreatmentEntryType;

import java.time.Instant;
import java.util.UUID;

public record TreatmentEntryDTO(
        UUID maMucDienBien,
        UUID maDotNoiTru,
        TreatmentEntryType loaiMuc,
        String noiDung,
        UUID nguoiGhi,
        Instant thoiGianGhi,
        UUID maMucBiDinhChinh) {
}
