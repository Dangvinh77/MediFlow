package com.mediflow.pharmacy.application.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.pharmacy.application.dto.command.CancelPrescriptionCommand;
import com.mediflow.pharmacy.application.dto.response.CancelPrescriptionResult;
import com.mediflow.pharmacy.application.event.PrescriptionCancelledEvent;
import com.mediflow.pharmacy.application.port.in.CancelPrescriptionUseCase;
import com.mediflow.pharmacy.application.port.out.DispenseSlipRepositoryPort;
import com.mediflow.pharmacy.application.port.out.PharmacyEventPublisherPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;
import com.mediflow.pharmacy.application.port.out.StockReservationRepositoryPort;
import com.mediflow.pharmacy.domain.exception.DispenseNotFoundException;
import com.mediflow.pharmacy.domain.exception.PrescriptionCancellationForbiddenException;
import com.mediflow.pharmacy.domain.exception.PrescriptionNotFoundException;
import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;
import com.mediflow.pharmacy.domain.model.DispenseSlip;
import com.mediflow.pharmacy.domain.model.Prescription;
import com.mediflow.pharmacy.domain.model.StockReservation;
import com.mediflow.pharmacy.domain.model.enums.ReservationReleaseReason;

import lombok.RequiredArgsConstructor;

/**
 * Điều phối việc hủy đơn thuốc và giải phóng toàn bộ lượng tồn đang giữ.
 *
 * <p>Aggregate được khóa theo thứ tự {@code Prescription -> DispenseSlip -> StockReservation}.
 * Tất cả reservation được kiểm tra trước khi thay đổi để transaction không chấp nhận một đơn
 * chỉ được giải phóng một phần. Thao tác lặp lại trên đơn đã hủy là idempotent.</p>
 */
@Service
@RequiredArgsConstructor
public class CancelPrescriptionService implements CancelPrescriptionUseCase {

    private final PrescriptionRepositoryPort prescriptionRepository;
    private final DispenseSlipRepositoryPort dispenseSlipRepository;
    private final StockReservationRepositoryPort reservationRepository;
    private final PharmacyEventPublisherPort eventPublisher;

    /**
     * Hủy một đơn đang hoạt động, kết thúc phiếu xuất và giải phóng các reservation.
     *
     * @param command lệnh chứa mã đơn, danh tính từ JWT và lý do hủy
     * @return trạng thái kết thúc cùng số reservation đã giải phóng
     * @throws PrescriptionNotFoundException nếu đơn không tồn tại
     * @throws PrescriptionCancellationForbiddenException nếu bác sĩ không phải người kê đơn
     * @throws PrescriptionRuleException nếu aggregate không còn ở trạng thái có thể hủy
     */
    @Override
    @Transactional
    public CancelPrescriptionResult cancel(CancelPrescriptionCommand command) {
        requireValidCommand(command);

        Prescription prescription = prescriptionRepository
                .findByIdForUpdate(command.prescriptionId())
                .orElseThrow(() -> new PrescriptionNotFoundException(
                        "Không tìm thấy đơn thuốc id=" + command.prescriptionId()));

        authorize(command, prescription);

        if (prescription.isCancelled()) {
            return new CancelPrescriptionResult(
                    prescription.getPrescriptionId(),
                    prescription.getStatus(),
                    0,
                    prescription.getCancelledAt());
        }
        if (!prescription.isActive()) {
            throw new PrescriptionRuleException(
                    "PRESCRIPTION_CANNOT_BE_CANCELLED",
                    "Chỉ đơn thuốc ACTIVE mới có thể bị hủy");
        }

        DispenseSlip slip = dispenseSlipRepository
                .findByPrescriptionForUpdate(command.prescriptionId())
                .orElseThrow(() -> new DispenseNotFoundException(
                        "Không tìm thấy phiếu xuất của đơn id=" + command.prescriptionId()));
        if (!slip.isPending()) {
            throw new PrescriptionRuleException(
                    "PRESCRIPTION_DISPENSE_NOT_PENDING",
                    "Không thể hủy đơn khi phiếu xuất không còn ở trạng thái PENDING");
        }

        List<StockReservation> reservations = reservationRepository
                .findByPrescriptionForUpdate(command.prescriptionId());
        requireAllReserved(reservations, command.prescriptionId());

        Instant cancelledAt = Instant.now();
        ReservationReleaseReason releaseReason = command.administrator()
                ? ReservationReleaseReason.ADMIN_OVERRIDE
                : ReservationReleaseReason.PRESCRIPTION_CANCELLED;

        for (StockReservation reservation : reservations) {
            reservation.release(releaseReason, command.actorId(), cancelledAt);
            reservationRepository.save(reservation);
        }

        prescription.cancel(command.actorId(), command.reason(), cancelledAt);
        slip.markCancelled(command.reason(), cancelledAt);
        Prescription savedPrescription = prescriptionRepository.save(prescription);
        dispenseSlipRepository.save(slip);

        eventPublisher.publishPrescriptionCancelled(new PrescriptionCancelledEvent(
                UUID.randomUUID(),
                cancelledAt,
                command.correlationId(),
                savedPrescription.getPrescriptionId(),
                savedPrescription.getPatientId(),
                command.actorId(),
                savedPrescription.getCancellationReason()));

        return new CancelPrescriptionResult(
                savedPrescription.getPrescriptionId(),
                savedPrescription.getStatus(),
                reservations.size(),
                savedPrescription.getCancelledAt());
    }

    /**
     * Xác thực các trường bắt buộc không thuộc request body validation.
     *
     * @param command lệnh do web adapter tạo
     */
    private void requireValidCommand(CancelPrescriptionCommand command) {
        if (command == null || command.prescriptionId() == null || command.actorId() == null) {
            throw new PrescriptionRuleException(
                    "PRESCRIPTION_CANCEL_COMMAND_INVALID",
                    "Mã đơn và người thực hiện hủy là bắt buộc");
        }
    }

    /**
     * Cho phép ADMIN hoặc chính bác sĩ đã kê đơn thực hiện thao tác.
     *
     * @param command lệnh hủy đã xác thực danh tính
     * @param prescription đơn thuốc đã khóa
     */
    private void authorize(
            CancelPrescriptionCommand command,
            Prescription prescription) {

        if (!command.administrator()
                && !command.actorId().equals(prescription.getDoctorId())) {
            throw new PrescriptionCancellationForbiddenException(
                    "Chỉ bác sĩ kê đơn hoặc quản trị viên được phép hủy đơn thuốc");
        }
    }

    /**
     * Bảo đảm aggregate reservation đầy đủ và chưa có dòng nào kết thúc.
     *
     * @param reservations các reservation đã khóa theo drugId
     * @param prescriptionId mã đơn dùng trong thông báo lỗi
     */
    private void requireAllReserved(
            List<StockReservation> reservations,
            UUID prescriptionId) {

        if (reservations.isEmpty()) {
            throw new PrescriptionRuleException(
                    "PRESCRIPTION_RESERVATION_MISSING",
                    "Đơn thuốc id=" + prescriptionId + " không có giữ chỗ tồn kho");
        }
        if (reservations.stream().anyMatch(reservation -> !reservation.isReserved())) {
            throw new PrescriptionRuleException(
                    "PRESCRIPTION_RESERVATION_INCONSISTENT",
                    "Không thể hủy đơn vì một phần giữ chỗ đã kết thúc");
        }
    }
}
