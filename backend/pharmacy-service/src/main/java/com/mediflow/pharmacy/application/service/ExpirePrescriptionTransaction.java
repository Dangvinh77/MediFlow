package com.mediflow.pharmacy.application.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.pharmacy.application.event.PrescriptionExpiredEvent;
import com.mediflow.pharmacy.application.port.out.DispenseSlipRepositoryPort;
import com.mediflow.pharmacy.application.port.out.PharmacyEventPublisherPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;
import com.mediflow.pharmacy.application.port.out.StockReservationRepositoryPort;
import com.mediflow.pharmacy.application.port.in.OutpatientPrescriptionLifecycleUseCase;
import com.mediflow.pharmacy.domain.model.DispenseSlip;
import com.mediflow.pharmacy.domain.model.Prescription;
import com.mediflow.pharmacy.domain.model.PrescriptionLine;
import com.mediflow.pharmacy.domain.model.StockReservation;
import com.mediflow.pharmacy.domain.model.enums.CareContractVersion;
import com.mediflow.pharmacy.domain.model.enums.CareContext;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * Kết thúc một prescription hết TTL trong một transaction độc lập.
 *
 * <p>Lớp được tách khỏi batch service để {@link Propagation#REQUIRES_NEW} đi qua Spring proxy.
 * Nếu một đơn lỗi hoặc bị transaction khác xử lý, các đơn trước đó vẫn giữ kết quả đã commit.</p>
 */
@Service
public class ExpirePrescriptionTransaction {

    private final PrescriptionRepositoryPort prescriptionRepository;
    private final DispenseSlipRepositoryPort dispenseSlipRepository;
    private final StockReservationRepositoryPort reservationRepository;
    private final PharmacyEventPublisherPort eventPublisher;
    private final java.util.Optional<OutpatientPrescriptionLifecycleUseCase> outpatientLifecycle;

    @Autowired
    public ExpirePrescriptionTransaction(PrescriptionRepositoryPort prescriptionRepository,
            DispenseSlipRepositoryPort dispenseSlipRepository, StockReservationRepositoryPort reservationRepository,
            PharmacyEventPublisherPort eventPublisher,
            java.util.Optional<OutpatientPrescriptionLifecycleUseCase> outpatientLifecycle) {
        this.prescriptionRepository = prescriptionRepository;
        this.dispenseSlipRepository = dispenseSlipRepository;
        this.reservationRepository = reservationRepository;
        this.eventPublisher = eventPublisher;
        this.outpatientLifecycle = outpatientLifecycle;
    }

    /** Compatibility construction has no activation authority for V1 expiry. */
    public ExpirePrescriptionTransaction(PrescriptionRepositoryPort prescriptionRepository,
            DispenseSlipRepositoryPort dispenseSlipRepository, StockReservationRepositoryPort reservationRepository,
            PharmacyEventPublisherPort eventPublisher) {
        this(prescriptionRepository, dispenseSlipRepository, reservationRepository, eventPublisher,
                java.util.Optional.empty());
    }

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
        // The existing REQUIRES_NEW job boundary/lease remains; no V1 event uses the legacy publisher.
        if (prescription.getCareContext().contractVersion() != CareContractVersion.LEGACY) {
            if (prescription.getCareContext().careContext() != CareContext.OUTPATIENT) return 0;
            return outpatientLifecycle.map(lifecycle -> lifecycle.expire(prescriptionId,
                    "outpatient-expiry-" + prescriptionId)).orElse(0);
        }

        DispenseSlip slip = dispenseSlipRepository
                .findByPrescriptionForUpdate(prescriptionId)
                .orElse(null);
        if (slip == null || !slip.isPending()) {
            return 0;
        }

        List<StockReservation> reservations = reservationRepository
                .findByPrescriptionForUpdate(prescriptionId);
        Set<UUID> expectedDrugIds = prescription.getLines().stream()
                .map(PrescriptionLine::getDrugId)
                .collect(Collectors.toSet());
        Set<UUID> actualDrugIds = reservations.stream()
                .map(StockReservation::getDrugId)
                .collect(Collectors.toSet());
        if (reservations.isEmpty()
                || reservations.size() != expectedDrugIds.size()
                || !actualDrugIds.equals(expectedDrugIds)
                || reservations.stream().anyMatch(reservation -> !reservation.isReserved())
                || reservations.stream().anyMatch(reservation -> !reservation.isExpiredAt(now))) {
            return 0;
        }
        for (PrescriptionLine line : prescription.getLines()) {
            boolean quantityMatches = reservations.stream()
                    .anyMatch(reservation -> reservation.getDrugId().equals(line.getDrugId())
                            && reservation.getQuantity() == line.getQuantity());
            if (!quantityMatches) {
                return 0;
            }
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
                UUID.nameUUIDFromBytes(("prescription.expired:" + prescriptionId)
                        .getBytes(StandardCharsets.UTF_8)),
                now,
                "reservation-expiry:" + prescriptionId,
                prescription.getPrescriptionId(),
                prescription.getPatientId(),
                reservations.size()));

        return reservations.size();
    }
}
