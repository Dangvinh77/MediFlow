package com.mediflow.pharmacy.application.service;

import com.mediflow.pharmacy.application.dto.response.DispenseDTO;
import com.mediflow.pharmacy.application.event.PrescriptionFilledEvent;
import com.mediflow.pharmacy.application.event.StockLowEvent;
import com.mediflow.pharmacy.application.mapper.DispenseDtoMapper;
import com.mediflow.pharmacy.application.port.out.DispenseSlipRepositoryPort;
import com.mediflow.pharmacy.application.port.out.DrugRepositoryPort;
import com.mediflow.pharmacy.application.port.out.PharmacyEventPublisherPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;
import com.mediflow.pharmacy.application.port.out.StockReservationRepositoryPort;
import com.mediflow.pharmacy.domain.exception.DispenseNotFoundException;
import com.mediflow.pharmacy.domain.exception.DrugNotFoundException;
import com.mediflow.pharmacy.domain.exception.DispenseRuleException;
import com.mediflow.pharmacy.domain.exception.StockReservationRuleException;
import com.mediflow.pharmacy.domain.model.DispenseSlip;
import com.mediflow.pharmacy.domain.model.Drug;
import com.mediflow.pharmacy.domain.model.Prescription;
import com.mediflow.pharmacy.domain.model.PrescriptionLine;
import com.mediflow.pharmacy.domain.model.StockReservation;
import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the single database transaction that changes stock and dispense lifecycle.
 *
 * <p>This bean is deliberately separate from the payment orchestrator. A failure leaves this
 * transaction before the compensation bean starts its {@code REQUIRES_NEW} transaction, so row
 * locks are never held across the compensation boundary.</p>
 */
@Service
public class DispenseTransactionService {

    private final PrescriptionRepositoryPort prescriptionRepo;
    private final DispenseSlipRepositoryPort dispenseSlipRepo;
    private final DrugRepositoryPort drugRepo;
    private final StockReservationRepositoryPort reservationRepo;
    private final PharmacyEventPublisherPort eventPublisher;
    private final DispenseDtoMapper dispenseDtoMapper;
    private final Clock clock;

    /** Creates the transaction executor with all application out-ports. */
    public DispenseTransactionService(
            PrescriptionRepositoryPort prescriptionRepo,
            DispenseSlipRepositoryPort dispenseSlipRepo,
            DrugRepositoryPort drugRepo,
            StockReservationRepositoryPort reservationRepo,
            PharmacyEventPublisherPort eventPublisher,
            DispenseDtoMapper dispenseDtoMapper,
            Clock clock) {
        this.prescriptionRepo = prescriptionRepo;
        this.dispenseSlipRepo = dispenseSlipRepo;
        this.drugRepo = drugRepo;
        this.reservationRepo = reservationRepo;
        this.eventPublisher = eventPublisher;
        this.dispenseDtoMapper = dispenseDtoMapper;
        this.clock = clock;
    }

    /**
     * Dispenses one prescription atomically.
     *
     * @param prescriptionId prescription to dispense
     * @param dispensedBy actor or system identity
     * @param correlationId saga correlation id
     * @return persisted dispense slip DTO
     */
    @Transactional
    public DispenseDTO execute(UUID prescriptionId, UUID dispensedBy, String correlationId) {
        Prescription prescription = prescriptionRepo.findByIdForUpdate(prescriptionId)
                .orElseThrow(() -> new com.mediflow.pharmacy.domain.exception.PrescriptionNotFoundException(
                        "Không tìm thấy đơn id=" + prescriptionId));
        DispenseSlip slip = dispenseSlipRepo.findByPrescriptionForUpdate(prescriptionId)
                .orElseThrow(() -> new DispenseNotFoundException(
                        "Không tìm thấy phiếu xuất của đơn id=" + prescriptionId));

        if (!slip.isPending()) {
            if (slip.getStatus() == DispenseStatus.DISPENSED) {
                return dispenseDtoMapper.toDto(slip);
            }
            throw new DispenseRuleException(
                    "DISPENSE_ALREADY_DONE", "Phiếu không còn ở trạng thái chờ xuất");
        }
        if (!prescription.isActive()) {
            throw new DispenseRuleException(
                    "PRESCRIPTION_NOT_ACTIVE", "Đơn thuốc không còn ở trạng thái ACTIVE");
        }

        List<UUID> sortedDrugIds = prescription.getLines().stream()
                .map(PrescriptionLine::getDrugId)
                .sorted()
                .toList();
        List<StockReservation> snapshot = reservationRepo.findByPrescription(prescriptionId);
        Set<UUID> expectedDrugIds = Set.copyOf(sortedDrugIds);
        Set<UUID> actualDrugIds = snapshot.stream()
                .map(StockReservation::getDrugId)
                .collect(Collectors.toSet());
        if (snapshot.size() != expectedDrugIds.size() || !actualDrugIds.equals(expectedDrugIds)) {
            throw new StockReservationRuleException(
                    "RESERVATION_SET_MISMATCH",
                    "Tập giữ chỗ không khớp toàn bộ dòng thuốc của đơn " + prescriptionId);
        }

        Map<UUID, Drug> lockedDrugs = new LinkedHashMap<>();
        for (UUID drugId : sortedDrugIds) {
            Drug drug = drugRepo.findByIdForUpdate(drugId)
                    .orElseThrow(() -> new DrugNotFoundException("Không tìm thấy thuốc id=" + drugId));
            int requestedQuantity = prescription.getLines().stream()
                    .filter(line -> line.getDrugId().equals(drugId))
                    .map(PrescriptionLine::getQuantity)
                    .findFirst()
                    .orElseThrow(() -> new StockReservationRuleException(
                            "PRESCRIPTION_LINE_MISSING", "Không tìm thấy dòng thuốc " + drugId));
            StockReservation reservation = reservationRepo
                    .findReservedByPrescriptionForUpdate(prescriptionId, drugId)
                    .orElseThrow(() -> new StockReservationRuleException(
                            "RESERVATION_MISSING", "Không tìm thấy giữ chỗ của thuốc id=" + drugId));
            if (!reservation.isReserved()) {
                throw new StockReservationRuleException(
                        "RESERVATION_INVALID_TRANSITION", "Giữ chỗ không còn hiệu lực");
            }
            if (reservation.isExpiredAt(Instant.now(clock))) {
                throw new StockReservationRuleException(
                        "RESERVATION_EXPIRED", "Giữ chỗ của thuốc id=" + drugId + " đã hết hạn");
            }
            if (reservation.getQuantity() != requestedQuantity) {
                throw new StockReservationRuleException(
                        "RESERVATION_QUANTITY_MISMATCH", "Số lượng giữ chỗ không khớp với đơn thuốc");
            }
            drug.dispenseStock(requestedQuantity, java.time.LocalDate.now(clock));
            reservation.markFulfilled();
            reservationRepo.save(reservation);
            lockedDrugs.put(drugId, drug);
        }

        lockedDrugs.values().forEach(drugRepo::save);
        Instant now = Instant.now(clock);
        slip.markDispensed(dispensedBy, now);
        DispenseSlip savedSlip = dispenseSlipRepo.save(slip);
        prescription.markFulfilled(now);
        prescriptionRepo.save(prescription);
        publishStockLow(lockedDrugs, correlationId);
        publishFilled(savedSlip, prescription, lockedDrugs, correlationId);
        return dispenseDtoMapper.toDto(savedSlip);
    }

    private void publishStockLow(Map<UUID, Drug> drugs, String correlationId) {
        drugs.values().stream()
                .filter(Drug::belowLowStockThreshold)
                .forEach(drug -> eventPublisher.publishStockLow(new StockLowEvent(
                        UUID.randomUUID(), Instant.now(clock), correlationId,
                        drug.getDrugId(), drug.getDrugName(), drug.getStockQuantity(),
                        drug.getLowStockThreshold())));
    }

    private void publishFilled(
            DispenseSlip slip,
            Prescription prescription,
            Map<UUID, Drug> drugs,
            String correlationId) {
        List<PrescriptionFilledEvent.DispensedItem> items = prescription.getLines().stream()
                .map(line -> new PrescriptionFilledEvent.DispensedItem(
                        line.getDrugId(),
                        drugs.get(line.getDrugId()).getDrugName(),
                        line.getQuantity()))
                .toList();
        eventPublisher.publishPrescriptionFilled(new PrescriptionFilledEvent(
                UUID.randomUUID(), Instant.now(clock), correlationId,
                slip.getPrescriptionId(), prescription.getPatientId(),
                prescription.getDepartmentId(), prescription.getTotalAmount(), items));
    }
}
