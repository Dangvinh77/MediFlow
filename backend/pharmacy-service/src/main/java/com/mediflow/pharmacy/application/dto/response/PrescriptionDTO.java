package com.mediflow.pharmacy.application.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;

/**
 * Toàn bộ đơn thuốc khi trả về cho client: thông tin đơn + tổng tiền (server tự tính)
 * + trạng thái phiếu xuất hiện tại (dispenseStatus) + danh sách các dòng.
 */
public record PrescriptionDTO(
        UUID prescriptionId, UUID recordId, UUID patientId, UUID doctorId,
        UUID departmentId, LocalDate prescribedDate, BigDecimal totalAmount,
        List<PrescriptionLineDTO> lines,
        DispenseStatus dispenseStatus, Instant createdAt
) {}
