package com.mediflow.pharmacy.application.dto.request;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

/**
 * Toàn bộ đơn thuốc bác sĩ kê — chỉ giữ 4 UUID tham chiếu sang service khác
 * (recordId, patientId, doctorId, departmentId), không chứa dữ liệu ngoài.
 * Danh sách lines phải có ít nhất 1 dòng, và mỗi dòng bên trong được validate nhờ @Valid.
 *
 * @param recordId medical record reference
 * @param patientId patient reference
 * @param doctorId prescribing doctor reference
 * @param departmentId department reference
 * @param prescribedDate prescription date
 * @param lines prescribed drug lines
 */
public record CreatePrescriptionRequest(
        @NotNull UUID recordId,
        @NotNull UUID patientId,
        @NotNull UUID doctorId,
        @NotNull UUID departmentId,
        @NotNull @PastOrPresent LocalDate prescribedDate,
        @NotEmpty @Valid List<PrescriptionLineRequest> lines
) {}
