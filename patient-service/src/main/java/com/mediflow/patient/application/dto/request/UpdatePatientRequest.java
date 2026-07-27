package com.mediflow.patient.application.dto.request;

import com.mediflow.patient.domain.model.GioiTinh;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Payload cập nhật bệnh nhân.
 *
 * <p>{@code soCmnd} is absent on purpose — it identifies the person and is immutable. Adding it
 * here would let a client quietly break the uniqueness rule.
 */
public record UpdatePatientRequest(

        @NotBlank @Size(max = 100)
        String hoTen,

        @NotNull @PastOrPresent(message = "Ngày sinh không được ở tương lai")
        LocalDate ngaySinh,

        @NotNull
        GioiTinh gioiTinh,

        @Size(max = 255)
        String diaChi,

        @Pattern(regexp = "\\d{10,15}", message = "Số điện thoại phải gồm 10-15 chữ số")
        String soDienThoai,

        @Email @Size(max = 100)
        String email,

        @Pattern(regexp = "\\d{2}-\\d{8}-\\d", message = "Số BHYT phải theo dạng XX-XXXXXXXX-X")
        String bhytSo
) {
}
