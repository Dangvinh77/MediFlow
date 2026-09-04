package com.mediflow.pharmacy.application.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.pharmacy.application.event.PrescriptionExpiredEvent;
import com.mediflow.pharmacy.application.port.out.DispenseSlipRepositoryPort;
import com.mediflow.pharmacy.application.port.out.PharmacyEventPublisherPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;
import com.mediflow.pharmacy.application.port.out.StockReservationRepositoryPort;
import com.mediflow.pharmacy.domain.model.DispenseSlip;
import com.mediflow.pharmacy.domain.model.Prescription;
import com.mediflow.pharmacy.domain.model.StockReservation;

import lombok.RequiredArgsConstructor;

/**
 * Kết thúc một prescription hết TTL trong một transaction độc lập.
 *
 * <p>Lớp được tách khỏi batch service để {@link Propagation#REQUIRES_NEW} đi qua Spring proxy.
 * Nếu một đơn lỗi hoặc bị transaction khác xử lý, các đơn trước đó vẫn giữ kết quả đã commit.</p>
 */
@Service
@RequiredArgsConstructor
public class ExpirePrescriptionTransaction {

    private final PrescriptionRepositoryPort prescriptionRepository;
    private final DispenseSlipRepositoryPort dispenseSlipRepository;
    private final StockReservationRepositoryPort reservationRepository;
    private final PharmacyEventPublisherPort eventPublisher;

    /**
     * Hết hạn một đơn nếu đơn, phiếu và toàn bộ reservation vẫn ở trạng thái hoạt động và
     * tất cả reservation đã đạt TTL tại cùng mốc thời gian.
     *
     * @param prescriptionId mã đơn ứng viên
     * @param now mốc thời gian chung của lần chạy batch
     * @return số reservation chuyển sang {@code EXPIRED}; bằng {@code 0} nếu không còn hợp lệ
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int expire(UUID prescriptionId, Instant now) {
        Prescription prescription = prescriptionRepository.findByIdForUpdate(prescriptionId)
                .orElse(null);
        if (prescription == null || !prescription.isActive()) {
            return 0;
        }

        DispenseSlip slip = dispenseSlipRepository
                .findByPrescriptionForUpdate(prescriptionId)
                .orElse(null);
        if (slip == null || !slip.isPending()) {
            return 0;
        }

        List<StockReservation> reservations = reservationRepository
                .findByPrescriptionForUpdate(prescriptionId);
        if (reservations.isEmpty()
                || reservations.stream().anyMatch(reservation -> !reservation.isReserved())
                || reservations.stream().anyMatch(reservation -> !reservation.isExpiredAt(now))) {
            return 0;
        }

        for (StockReservation reservation : reservations) {
            reservation.expire(now);
            reservationRepository.save(reservation);
        }

        prescription.markExpired(now);
        slip.markExpired(now);
        prescriptionRepository.save(prescription);
        dispenseSlipRepository.save(slip);

        eventPublisher.publishPrescriptionExpired(new PrescriptionExpiredEvent(
                UUID.randomUUID(),
                now,
                null,
                prescription.getPrescriptionId(),
                prescription.getPatientId(),
                reservations.size()));

        return reservations.size();
    }
}
