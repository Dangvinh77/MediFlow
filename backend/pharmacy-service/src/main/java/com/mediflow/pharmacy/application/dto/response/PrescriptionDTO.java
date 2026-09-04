package com.mediflow.pharmacy.application.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;
import com.mediflow.pharmacy.domain.model.enums.PrescriptionStatus;

/**
 * Toàn bộ đơn thuốc trả về cho client, bao gồm trạng thái vòng đời và audit hủy.
 *
 * @param prescriptionId mã đơn thuốc
 * @param recordId mã hồ sơ bệnh án
 * @param patientId mã bệnh nhân
 * @param doctorId mã bác sĩ kê đơn
 * @param departmentId mã khoa
 * @param prescribedDate ngày kê đơn
 * @param totalAmount tổng tiền do server tính
 * @param lines các dòng thuốc
 * @param status trạng thái vòng đời của đơn
 * @param dispenseStatus trạng thái phiếu xuất
 * @param cancelledAt thời điểm hủy, nếu có
 * @param cancelledBy người hủy, nếu có
 * @param cancellationReason lý do hủy, nếu có
 * @param createdAt thời điểm tạo
 * @param updatedAt thời điểm cập nhật cuối
 */
public record PrescriptionDTO(
        UUID prescriptionId, UUID recordId, UUID patientId, UUID doctorId,
        UUID departmentId, LocalDate prescribedDate, BigDecimal totalAmount,
        List<PrescriptionLineDTO> lines,
        PrescriptionStatus status, DispenseStatus dispenseStatus,
        Instant cancelledAt, UUID cancelledBy, String cancellationReason,
        Instant createdAt, Instant updatedAt
) {}
