package com.mediflow.pharmacy.application.service;

import com.mediflow.pharmacy.application.dto.command.CreatePrescriptionCommand;
import com.mediflow.pharmacy.application.dto.request.CreatePrescriptionRequest;
import com.mediflow.pharmacy.application.dto.request.PrescriptionLineRequest;
import com.mediflow.pharmacy.application.dto.response.PrescriptionDTO;
import com.mediflow.pharmacy.application.dto.response.PrescriptionLineDTO;
import com.mediflow.pharmacy.application.event.PrescriptionCreatedEvent;
import com.mediflow.pharmacy.application.mapper.PrescriptionDtoMapper;
import com.mediflow.pharmacy.application.port.in.CreatePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.GetPrescriptionUseCase;
import com.mediflow.pharmacy.application.port.out.DispenseSlipRepositoryPort;
import com.mediflow.pharmacy.application.port.out.DrugRepositoryPort;
import com.mediflow.pharmacy.application.port.out.PharmacyEventPublisherPort;
import com.mediflow.pharmacy.application.port.out.PaymentReceiptRepositoryPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;
import com.mediflow.pharmacy.application.port.out.StockReservationRepositoryPort;
import com.mediflow.pharmacy.domain.exception.DispenseNotFoundException;
import com.mediflow.pharmacy.domain.exception.DrugNotFoundException;
import com.mediflow.pharmacy.domain.exception.PrescriptionCreationForbiddenException;
import com.mediflow.pharmacy.domain.exception.PrescriptionNotFoundException;
import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;
import com.mediflow.pharmacy.domain.exception.StockReservationRuleException;
import com.mediflow.pharmacy.domain.model.DispenseSlip;
import com.mediflow.pharmacy.domain.model.Drug;
import com.mediflow.pharmacy.domain.model.PaymentReceipt;
import com.mediflow.pharmacy.domain.model.Prescription;
import com.mediflow.pharmacy.domain.model.PrescriptionLine;
import com.mediflow.pharmacy.domain.model.StockReservation;
import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;
import com.mediflow.pharmacy.domain.model.enums.PaymentReceiptStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for prescription creation and read-only detail queries.
 *
 * <p>Create locks drugs in deterministic order, snapshots price/name, persists the complete
 * prescription aggregate and writes one pending dispense slip before the transaction ends.</p>
 */
@Service
public class PrescriptionApplicationService implements CreatePrescriptionUseCase, GetPrescriptionUseCase {

    private final DrugRepositoryPort drugRepository;
    private final PrescriptionRepositoryPort prescriptionRepository;
    private final DispenseSlipRepositoryPort dispenseSlipRepository;
    private final StockReservationRepositoryPort reservationRepository;
    private final PharmacyEventPublisherPort eventPublisher;
    private final PaymentReceiptRepositoryPort paymentReceipts;
    private final PrescriptionDtoMapper prescriptionDtoMapper;
    private final Clock clock;
    private final Duration reservationTtl;

    /** Creates the prescription feature service. */
    public PrescriptionApplicationService(
            DrugRepositoryPort drugRepository,
            PrescriptionRepositoryPort prescriptionRepository,
            DispenseSlipRepositoryPort dispenseSlipRepository,
            StockReservationRepositoryPort reservationRepository,
            PharmacyEventPublisherPort eventPublisher,
            PaymentReceiptRepositoryPort paymentReceipts,
            PrescriptionDtoMapper prescriptionDtoMapper,
            Clock clock,
            Duration reservationTtl) {
        if (reservationTtl == null || reservationTtl.isZero() || reservationTtl.isNegative()) {
            throw new IllegalArgumentException("mediflow.pharmacy.reservation.ttl must be positive");
        }
        this.drugRepository = drugRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.dispenseSlipRepository = dispenseSlipRepository;
        this.reservationRepository = reservationRepository;
        this.eventPublisher = eventPublisher;
        this.paymentReceipts = paymentReceipts;
        this.prescriptionDtoMapper = prescriptionDtoMapper;
        this.clock = clock;
        this.reservationTtl = reservationTtl;
    }

    /** Creates prescription, reservations, pending slip and created event atomically. */
    @Override
    @Transactional
    public PrescriptionDTO create(CreatePrescriptionCommand command) {
        CreatePrescriptionRequest request = command.request();
        validateCreator(command);
        validateNoDuplicateDrugIds(request.lines());

        List<ResolvedPrescriptionLine> resolvedLines = request.lines().stream()
                .sorted(java.util.Comparator.comparing(PrescriptionLineRequest::drugId))
                .map(this::resolveLine)
                .toList();
        List<PrescriptionLine> lines = resolvedLines.stream()
                .map(ResolvedPrescriptionLine::line)
                .toList();

        Prescription prescription = Prescription.create(
                request.recordId(),
                request.patientId(),
                command.actor().isAdministrator() ? request.doctorId() : command.actor().requireStaffId(),
                request.departmentId(),
                request.prescribedDate(),
                lines);
        Prescription savedPrescription = prescriptionRepository.save(prescription);
        Instant expiresAt = Instant.now(clock).plus(reservationTtl);
        for (ResolvedPrescriptionLine resolved : resolvedLines) {
            reservationRepository.save(StockReservation.create(
                    resolved.line().getDrugId(),
                    savedPrescription.getPrescriptionId(),
                    resolved.line().getQuantity(),
                    expiresAt));
        }

        DispenseSlip pendingSlip = dispenseSlipRepository.save(
                DispenseSlip.createPending(savedPrescription.getPrescriptionId()));
        Map<UUID, String> drugNames = resolvedLines.stream().collect(Collectors.toMap(
                resolved -> resolved.line().getDrugId(),
                ResolvedPrescriptionLine::drugName,
                (first, ignored) -> first,
                LinkedHashMap::new));
        publishCreated(savedPrescription, drugNames, command.correlationId());
        return toDto(savedPrescription, pendingSlip.getStatus(), false, drugNames);
    }

    /** Reads prescription detail and current dispense lifecycle without a write lock. */
    @Override
    @Transactional(readOnly = true)
    public PrescriptionDTO getPrescriptionById(UUID prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new PrescriptionNotFoundException(
                        "Không tìm thấy đơn thuốc id=" + prescriptionId));
        DispenseSlip slip = dispenseSlipRepository.findByPrescription(prescriptionId)
                .orElseThrow(() -> new DispenseNotFoundException(
                        "Không tìm thấy phiếu xuất của đơn id=" + prescriptionId));
        List<UUID> drugIds = prescription.getLines().stream()
                .map(PrescriptionLine::getDrugId)
                .distinct()
                .toList();
        Map<UUID, String> drugNames = drugRepository.findByIds(drugIds).stream().collect(Collectors.toMap(
                Drug::getDrugId,
                Drug::getDrugName,
                (first, ignored) -> first,
                LinkedHashMap::new));
        drugIds.stream().filter(id -> !drugNames.containsKey(id)).forEach(id -> drugNames.put(id, null));
        return toDto(prescription, slip.getStatus(), hasPaymentConfirmation(prescriptionId), drugNames);
    }

    private void validateCreator(CreatePrescriptionCommand command) {
        if (!command.actor().isAdministrator()) {
            UUID staffId;
            try {
                staffId = command.actor().requireStaffId();
            } catch (IllegalStateException exception) {
                throw new PrescriptionCreationForbiddenException(
                        "Không thể tạo đơn khi JWT chưa cung cấp staffId đã xác thực");
            }
            if (!staffId.equals(command.request().doctorId())) {
                throw new PrescriptionCreationForbiddenException(
                        "Bác sĩ chỉ được tạo đơn thuốc bằng danh tính của chính mình");
            }
        }
    }

    private void validateNoDuplicateDrugIds(List<PrescriptionLineRequest> requests) {
        Set<UUID> seen = new HashSet<>();
        for (PrescriptionLineRequest request : requests) {
            if (!seen.add(request.drugId())) {
                throw new PrescriptionRuleException(
                        "PRESCRIPTION_DUPLICATE_DRUG",
                        "Thuốc id=" + request.drugId() + " xuất hiện nhiều hơn một lần trong đơn");
            }
        }
    }

    private ResolvedPrescriptionLine resolveLine(PrescriptionLineRequest request) {
        Drug drug = drugRepository.findByIdForUpdate(request.drugId())
                .orElseThrow(() -> new DrugNotFoundException(
                        "Không tìm thấy thuốc id=" + request.drugId()));
        int reservedQuantity = reservationRepository.findReservedByDrug(request.drugId()).stream()
                .mapToInt(StockReservation::getQuantity)
                .sum();
        int available = drug.getStockQuantity() - reservedQuantity;
        if (available < request.quantity()) {
            throw new StockReservationRuleException(
                    "INSUFFICIENT_AVAILABLE_STOCK",
                    "Không đủ thuốc có thể bán cho '" + drug.getDrugName() + "': cần "
                            + request.quantity() + ", còn " + Math.max(available, 0));
        }
        return new ResolvedPrescriptionLine(
                PrescriptionLine.create(
                        request.drugId(), request.quantity(), drug.getPrice(), request.dosage()),
                drug.getDrugName());
    }

    private void publishCreated(
            Prescription prescription,
            Map<UUID, String> drugNames,
            String correlationId) {
        List<PrescriptionCreatedEvent.Item> items = prescription.getLines().stream()
                .map(line -> new PrescriptionCreatedEvent.Item(
                        line.getDrugId(),
                        drugNames.get(line.getDrugId()),
                        line.getQuantity(),
                        line.getUnitPrice()))
                .toList();
        eventPublisher.publishPrescriptionCreated(new PrescriptionCreatedEvent(
                UUID.randomUUID(),
                Instant.now(clock),
                correlationId,
                prescription.getPrescriptionId(),
                prescription.getPatientId(),
                prescription.getRecordId(),
                prescription.getDepartmentId(),
                prescription.getTotalAmount(),
                items));
    }

    private PrescriptionDTO toDto(
            Prescription prescription,
            DispenseStatus dispenseStatus,
            boolean paymentConfirmed,
            Map<UUID, String> drugNames) {
        List<PrescriptionLineDTO> lines = prescription.getLines().stream()
                .map(line -> prescriptionDtoMapper.toLineDto(line, drugNames.get(line.getDrugId())))
                .toList();
        return prescriptionDtoMapper.toDto(prescription, dispenseStatus, paymentConfirmed, lines);
    }

    /**
     * Pharmacy only exposes a confirmed payment after its own durable receipt is present. A
     * compensated receipt does not authorize a manual dispense.
     */
    private boolean hasPaymentConfirmation(UUID prescriptionId) {
        List<PaymentReceipt> receipts = paymentReceipts.findByPrescriptionId(prescriptionId);
        return receipts != null && receipts.stream().anyMatch(receipt -> receipt != null
                && (receipt.getStatus() == PaymentReceiptStatus.RECEIVED
                || receipt.getStatus() == PaymentReceiptStatus.DISPENSED));
    }

    /** Holds a server-resolved price/name beside a prescription line during one use case. */
    private record ResolvedPrescriptionLine(PrescriptionLine line, String drugName) {
    }
}
