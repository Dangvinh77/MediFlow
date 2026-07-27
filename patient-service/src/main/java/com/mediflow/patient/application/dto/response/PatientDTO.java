package com.mediflow.patient.application.dto.response;

import com.mediflow.patient.domain.model.GioiTinh;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Patient response payload. Field names are Vietnamese camelCase, matching the JSON contract in
 * docs/ai/05-api-conventions.md and the TypeScript types in {@code frontend/src/lib/types.ts}.
 * The domain model never crosses the boundary.
 */
public record PatientDTO(
        UUID maBenhNhan,
        String hoTen,
        LocalDate ngaySinh,
        GioiTinh gioiTinh,
        String soCmnd,
        String diaChi,
        String soDienThoai,
        String email,
        String bhytSo,
        Instant createdAt,
        Instant updatedAt
) {
}
