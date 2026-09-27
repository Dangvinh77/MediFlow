package com.mediflow.inpatient.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateBedRequest(
        @NotNull UUID maKhoa,
        @NotBlank @Size(max = 32) String maKhu,
        @NotBlank @Size(max = 32) String maPhong,
        @NotBlank @Size(max = 32) String maGiuong,
        @NotBlank @Size(max = 32) String loaiGiuong) {
}
