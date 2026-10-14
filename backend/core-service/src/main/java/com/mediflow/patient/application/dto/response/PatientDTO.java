package com.mediflow.patient.application.dto.response;

import com.mediflow.patient.domain.model.Gender;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Public Vietnamese wire contract consumed by the existing frontend. */
public record PatientDTO(
        UUID maBenhNhan,
        String hoTen,
        LocalDate ngaySinh,
        Gender gioiTinh,
        String soCmnd,
        String diaChi,
        String soDienThoai,
        String email,
        String bhytSo,
        Instant createdAt,
        Instant updatedAt) {
}
