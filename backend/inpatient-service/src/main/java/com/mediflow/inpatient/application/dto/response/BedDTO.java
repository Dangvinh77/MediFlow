package com.mediflow.inpatient.application.dto.response;

import com.mediflow.inpatient.domain.model.enums.BedStatus;

import java.util.UUID;

public record BedDTO(
        UUID maGiuong,
        UUID maKhoa,
        String maKhu,
        String maPhong,
        String maGiuongTrongPhong,
        String loaiGiuong,
        BedStatus status,
        boolean active) {
}
