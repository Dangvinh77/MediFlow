package com.mediflow.inpatient.application.dto.response;

import com.mediflow.inpatient.domain.model.enums.AdmissionPriority;
import com.mediflow.inpatient.domain.model.enums.AdmissionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdmissionDTO(
        UUID maDotNoiTru,
        UUID maYeuCauNoiTru,
        UUID maBenhNhan,
        UUID maHoSoNguon,
        UUID maKhoa,
        AdmissionPriority doUuTien,
        boolean capCuu,
        AdmissionStatus status,
        UUID maGiuongDangSuDung,
        UUID maXacNhanTamUng,
        UUID maQuyetToan,
        UUID maTomTatRaVien,
        UUID maPheDuyetNgoaiLe,
        Instant thoiGianYeuCau,
        Instant thoiGianNhapVien,
        Instant thoiGianRaVienYTe,
        Instant thoiGianDong,
        Instant thoiGianHuy,
        String lyDoHuy,
        List<ClinicalOrderReferenceDTO> yeuLenhNgoai) {

    public AdmissionDTO {
        yeuLenhNgoai = yeuLenhNgoai == null ? List.of() : List.copyOf(yeuLenhNgoai);
    }
}
