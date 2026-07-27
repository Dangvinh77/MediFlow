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
 * Payload tiếp nhận bệnh nhân mới.
 *
 * <p>These annotations catch a malformed HTTP request early and return a 400 with per-field
 * detail. They do <em>not</em> replace the invariants in {@code Patient} — those return 422 and
 * protect the model from every caller, not just from HTTP.
 */
public record CreatePatientRequest(

        @NotBlank @Size(max = 100)
        String hoTen,

        @NotNull @PastOrPresent(message = "Ngày sinh không được ở tương lai")
        LocalDate ngaySinh,

        @NotNull
        GioiTinh gioiTinh,

        @NotBlank @Size(max = 20)
        String soCmnd,

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
