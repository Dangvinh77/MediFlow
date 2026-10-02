package com.mediflow.patient.application.dto.request;

import com.mediflow.patient.domain.model.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** soCmnd is intentionally absent: the identity number is immutable after creation. */
public record UpdatePatientRequest(
        @NotBlank(message = "hoTen không được để trống")
        @Size(max = 100, message = "hoTen tối đa 100 ký tự")
        String hoTen,

        @NotNull(message = "ngaySinh không được để trống")
        @PastOrPresent(message = "ngaySinh không được ở tương lai")
        LocalDate ngaySinh,

        @NotNull(message = "gioiTinh không được để trống")
        Gender gioiTinh,

        @Size(max = 255, message = "diaChi tối đa 255 ký tự")
        String diaChi,

        @Pattern(regexp = "\\d{10,15}", message = "soDienThoai phải gồm 10 đến 15 chữ số")
        String soDienThoai,

        @Email(message = "email không đúng định dạng")
        @Size(max = 100, message = "email tối đa 100 ký tự")
        String email,

        @Pattern(regexp = "\\d{2}-\\d{8}-\\d", message = "bhytSo không đúng định dạng")
        String bhytSo) {
}
