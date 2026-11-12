package com.mediflow.pharmacy.application.service;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.dto.command.PrescriptionRevisionBillingPayload;
import com.mediflow.pharmacy.application.dto.command.UpdatePrescriptionCommand;
import com.mediflow.pharmacy.application.dto.request.PrescriptionLineRequest;
import com.mediflow.pharmacy.application.dto.response.PrescriptionDTO;
import com.mediflow.pharmacy.application.event.carefinance.PrescriptionCareEvent;
import com.mediflow.pharmacy.application.mapper.PrescriptionCareEventFactory;
import com.mediflow.pharmacy.application.model.PrescriptionRevisionOperation;
import com.mediflow.pharmacy.application.port.out.DispenseSlipRepositoryPort;
import com.mediflow.pharmacy.application.port.out.DrugRepositoryPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionCareEventWriterPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionClearancePort;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionRevisionOperationPort;
import com.mediflow.pharmacy.application.port.out.StockReservationRepositoryPort;
import com.mediflow.pharmacy.domain.exception.DispenseNotFoundException;
import com.mediflow.pharmacy.domain.exception.DrugNotFoundException;
import com.mediflow.pharmacy.domain.exception.PrescriptionNotFoundException;
import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;
import com.mediflow.pharmacy.domain.exception.StockReservationRuleException;
import com.mediflow.pharmacy.domain.model.Drug;
import com.mediflow.pharmacy.domain.model.Prescription;
import com.mediflow.pharmacy.domain.model.PrescriptionLine;
import com.mediflow.pharmacy.domain.model.StockReservation;
import com.mediflow.pharmacy.domain.model.enums.CareContext;
import com.mediflow.pharmacy.domain.model.enums.CareContractVersion;
import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;
import com.mediflow.pharmacy.domain.model.enums.ReservationReleaseReason;
import com.mediflow.pharmacy.domain.model.enums.ReservationStatus;
import com.mediflow.pharmacy.domain.model.enums.PrescriptionStatus;
import com.mediflow.pharmacy.application.port.out.OutpatientPrescriptionContextPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionIdentityPort;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Local transaction boundary for durable revision receipts and Pharmacy-owned state. */
@Service
public class CarePrescriptionRevisionTransactionService {
    private final PrescriptionRevisionOperationPort operations;
    private final PrescriptionRepositoryPort prescriptions;
    private final DrugRepositoryPort drugs;
    private final DispenseSlipRepositoryPort slips;
    private final StockReservationRepositoryPort reservations;
    private final PrescriptionClearancePort clearances;
    private final PrescriptionCareEventWriterPort careEvents;
    private final Clock clock;

    public CarePrescriptionRevisionTransactionService(PrescriptionRevisionOperationPort operations,
            PrescriptionRepositoryPort prescriptions, DrugRepositoryPort drugs, DispenseSlipRepositoryPort slips,
            StockReservationRepositoryPort reservations, PrescriptionClearancePort clearances,
            PrescriptionCareEventWriterPort careEvents, Clock clock) {
        this.operations = operations;
        this.prescriptions = prescriptions;
        this.drugs = drugs;
        this.slips = slips;
        this.reservations = reservations;
        this.clearances = clearances;
        this.careEvents = careEvents;
        this.clock = clock;
    }

    @Transactional
    public PrescriptionRevisionOperation begin(UpdatePrescriptionCommand command,
            OutpatientPrescriptionContextPort.Observation context,
            PrescriptionIdentityPort.Observation identity) {
        String fingerprint = fingerprint(command);
        Optional<PrescriptionRevisionOperation> existing = operations.findByIdForUpdate(command.operationId());
        if (existing.isPresent()) {
            requireSameIntent(existing.get(), command, fingerprint);
            return existing.get();
        }

        Prescription prescription = prescriptions.findByIdForUpdate(command.prescriptionId())
                .orElseThrow(() -> notFound(command.prescriptionId()));
        existing = operations.findByIdForUpdate(command.operationId());
        if (existing.isPresent()) {
            requireSameIntent(existing.get(), command, fingerprint);
            requireEditable(prescription, command.actor(), command.request().expectedRevision());
            requireProof(context, identity, prescription, clock.instant());
            return existing.get();
        }
        requireEditable(prescription, command.actor(), command.request().expectedRevision());
        Instant now = clock.instant();
        requireProof(context, identity, prescription, now);
        operations.findActiveByPrescription(prescription.getPrescriptionId()).ifPresent(active -> {
            throw new DuplicateResourceException("PRESCRIPTION_REVISION_IN_PROGRESS",
                    "Another edit for this prescription is still being reconciled");
        });
        var slip = slips.findByPrescription(prescription.getPrescriptionId())
                .orElseThrow(() -> new DispenseNotFoundException("Prescription dispense slip was not found"));
        if (slip.getStatus() != DispenseStatus.PENDING) {
            throw new PrescriptionRuleException("PRESCRIPTION_DISPENSE_NOT_PENDING", "Only a pending prescription can be edited");
        }
        requireNoPaymentClearance(prescription);

        List<PrescriptionLineRequest> requested = validateAndSort(command.request().lines());
        LocalDate businessDate = now.atZone(clock.getZone()).toLocalDate();
        List<PrescriptionRevisionBillingPayload.Item> items = new ArrayList<>();
        Map<UUID, Drug> lockedDrugs = new LinkedHashMap<>();
        for (PrescriptionLineRequest line : requested) {
            Drug drug = drugs.findByIdForUpdate(line.drugId())
                    .orElseThrow(() -> new DrugNotFoundException("Requested drug is unavailable"));
            if (drug.getExpiryDate().isBefore(businessDate)) {
                throw new PrescriptionRuleException("DRUG_EXPIRED", "An expired drug cannot be added to a prescription");
            }
            lockedDrugs.put(drug.getDrugId(), drug);
            items.add(new PrescriptionRevisionBillingPayload.Item(
                    drug.getDrugId(), drug.getDrugName(), line.quantity(), drug.getPrice()));
        }
        requireProof(context, identity, prescription, clock.instant());
        var care = prescription.getCareContext();
        var payload = new PrescriptionRevisionBillingPayload(prescription.getPrescriptionId(), prescription.getRecordId(),
                null, prescription.getPatientId(), prescription.getDepartmentId(), CareContext.OUTPATIENT,
                care.episode().type(), care.episode().id(), "PRESCRIPTION", prescription.getPrescriptionId(),
                care.priceCode(), items, total(items), prescription.getRevision(), Math.addExact(prescription.getRevision(), 1));
        var operation = new PrescriptionRevisionOperation(command.operationId(), prescription.getPrescriptionId(),
                command.request().expectedRevision(), command.actor(), command.correlationId(), fingerprint,
                requested, payload, PrescriptionRevisionOperation.Status.PENDING, null, null, now, now);
        return operations.create(operation);
    }

    @Transactional(readOnly = true)
    public PrescriptionRevisionOperation get(UUID operationId) {
        return operations.findById(operationId)
                .orElseThrow(() -> new PrescriptionRuleException("PRESCRIPTION_REVISION_NOT_FOUND", "Revision operation was not found"));
    }

    @Transactional(readOnly = true)
    public Optional<PrescriptionRevisionOperation> find(UUID operationId) {
        return operations.findById(operationId);
    }

    @Transactional(readOnly = true)
    public void verifyIntent(PrescriptionRevisionOperation operation, UpdatePrescriptionCommand command) {
        requireSameIntent(operation, command, fingerprint(command));
    }

    @Transactional
    public PrescriptionRevisionOperation markPrepared(UUID operationId) {
        var operation = lock(operationId);
        if (operation.status() == PrescriptionRevisionOperation.Status.PENDING) {
            return operations.update(operation.transition(PrescriptionRevisionOperation.Status.PREPARED,
                    null, null, clock.instant()), PrescriptionRevisionOperation.Status.PENDING);
        }
        return operation;
    }

    @Transactional
    public PrescriptionRevisionOperation markAborting(UUID operationId, String code, String message) {
        var operation = lock(operationId);
        if (operation.status() == PrescriptionRevisionOperation.Status.PENDING
                || operation.status() == PrescriptionRevisionOperation.Status.PREPARED) {
            String safeCode = code == null || code.isBlank() ? "PRESCRIPTION_REVISION_ABORTED" : code;
            String safeMessage = message == null || message.isBlank() ? "Prescription revision was not applied" : message;
            if (safeMessage.length() > 500) safeMessage = safeMessage.substring(0, 500);
            return operations.update(operation.transition(PrescriptionRevisionOperation.Status.ABORTING,
                    safeCode, safeMessage, clock.instant()), operation.status());
        }
        return operation;
    }

    @Transactional
    public PrescriptionRevisionOperation markAborted(UUID operationId) {
        var operation = lock(operationId);
        if (operation.status() == PrescriptionRevisionOperation.Status.ABORTING) {
            return operations.update(operation.transition(PrescriptionRevisionOperation.Status.ABORTED,
                    operation.failureCode(), operation.failureMessage(), clock.instant()),
                    PrescriptionRevisionOperation.Status.ABORTING);
        }
        return operation;
    }

    /**
     * Any payment clearance recorded for this exact prescription means money was collected, whether or not its
     * dispense target was verified yet or its dispense window has since expired. Such a prescription is never edited.
     */
    private void requireNoPaymentClearance(Prescription prescription) {
        if (clearances.findByTargetForUpdate(prescription.getPrescriptionId()).stream()
                .anyMatch(grant -> grant.matches(prescription))) {
            throw new PrescriptionRuleException("PRESCRIPTION_ALREADY_PAID", "A paid prescription cannot be edited");
        }
    }

    @Transactional
    public PrescriptionRevisionOperation apply(UUID operationId,
            OutpatientPrescriptionContextPort.Observation context,
            PrescriptionIdentityPort.Observation identity) {
        var operation = lock(operationId);
        if (operation.status() == PrescriptionRevisionOperation.Status.APPLIED
                || operation.status() == PrescriptionRevisionOperation.Status.COMMITTED) return operation;
        if (operation.status() != PrescriptionRevisionOperation.Status.PREPARED) {
            throw new IllegalStateException("Billing prepare must be durable before Pharmacy applies a revision");
        }
        Prescription prescription = prescriptions.findByIdForUpdate(operation.prescriptionId())
                .orElseThrow(() -> notFound(operation.prescriptionId()));
        requireEditable(prescription, operation.actor(), operation.expectedRevision());
        requireNoPaymentClearance(prescription);
        Instant now = clock.instant();
        requireProof(context, identity, prescription, now);
        var slip = slips.findByPrescriptionForUpdate(prescription.getPrescriptionId())
                .orElseThrow(() -> new DispenseNotFoundException("Prescription dispense slip was not found"));
        if (slip.getStatus() != DispenseStatus.PENDING) {
            throw new PrescriptionRuleException("PRESCRIPTION_DISPENSE_NOT_PENDING", "Only a pending prescription can be edited");
        }
        List<StockReservation> allReservations = reservations.findByPrescriptionForUpdate(prescription.getPrescriptionId());
        List<StockReservation> currentReservations = allReservations.stream().filter(StockReservation::isReserved).toList();
        Map<UUID, StockReservation> byDrug = new HashMap<>();
        for (StockReservation reservation : allReservations) byDrug.put(reservation.getDrugId(), reservation);
        Map<UUID, StockReservation> currentByDrug = new HashMap<>();
        for (StockReservation reservation : currentReservations) currentByDrug.put(reservation.getDrugId(), reservation);
        Map<UUID, PrescriptionLine> oldLines = prescription.getLines().stream()
                .collect(java.util.stream.Collectors.toMap(PrescriptionLine::getDrugId, line -> line));
        if (currentByDrug.size() != oldLines.size() || !currentByDrug.keySet().equals(oldLines.keySet())
                || currentReservations.stream().anyMatch(hold -> oldLines.get(hold.getDrugId()).getQuantity() != hold.getQuantity())) {
            throw new StockReservationRuleException("RESERVATION_MISMATCH", "Exact current prescription stock reservations are required");
        }
        Instant expiresAt = allReservations.stream().map(StockReservation::getExpiresAt)
                .filter(java.util.Objects::nonNull).min(Comparator.naturalOrder())
                .orElseThrow(() -> new StockReservationRuleException("RESERVATION_MISMATCH", "Prescription reservation expiry is required"));
        Instant reservationCheckedAt = now;
        if (!expiresAt.isAfter(reservationCheckedAt)
                || currentReservations.stream().anyMatch(hold -> hold.isExpiredAt(reservationCheckedAt))) {
            throw new StockReservationRuleException("RESERVATION_EXPIRED", "An expired prescription reservation cannot be extended by editing");
        }

        List<PrescriptionLineRequest> requested = operation.requestedLines();
        Map<UUID, PrescriptionRevisionBillingPayload.Item> priced = operation.billingPayload().items().stream()
                .collect(java.util.stream.Collectors.toMap(PrescriptionRevisionBillingPayload.Item::drugId, item -> item));
        Set<UUID> toLock = new HashSet<>(oldLines.keySet());
        requested.forEach(line -> toLock.add(line.drugId()));
        Map<UUID, Drug> lockedDrugs = new LinkedHashMap<>();
        for (UUID drugId : toLock.stream().sorted().toList()) {
            Drug drug = drugs.findByIdForUpdate(drugId).orElseThrow(() -> new DrugNotFoundException("Prescribed drug is unavailable"));
            lockedDrugs.put(drugId, drug);
        }
        now = clock.instant();
        requireProof(context, identity, prescription, now);
        LocalDate businessDate = now.atZone(clock.getZone()).toLocalDate();
        Instant reservationsCheckedAfterDrugLocks = now;
        if (!expiresAt.isAfter(reservationsCheckedAfterDrugLocks)
                || currentReservations.stream().anyMatch(hold -> hold.isExpiredAt(reservationsCheckedAfterDrugLocks))) {
            throw new StockReservationRuleException("RESERVATION_EXPIRED", "An expired prescription reservation cannot be extended by editing");
        }
        List<PrescriptionLine> replacementLines = new ArrayList<>();
        for (PrescriptionLineRequest line : requested) {
            Drug drug = lockedDrugs.get(line.drugId());
            var pricedItem = priced.get(line.drugId());
            if (pricedItem == null || pricedItem.quantity() != line.quantity()
                    || drug.getExpiryDate().isBefore(businessDate)) {
                throw new PrescriptionRuleException("PRESCRIPTION_REVISION_SNAPSHOT_INVALID", "Stored priced line no longer matches the revision intent");
            }
            long reserved = reservations.findReservedByDrug(line.drugId()).stream()
                    .mapToLong(StockReservation::getQuantity).sum();
            long ownReserved = currentByDrug.containsKey(line.drugId()) ? currentByDrug.get(line.drugId()).getQuantity() : 0;
            if ((long) drug.getStockQuantity() - (reserved - ownReserved) < line.quantity()) {
                throw new StockReservationRuleException("INSUFFICIENT_AVAILABLE_STOCK", "Insufficient available stock for prescription revision");
            }
            replacementLines.add(PrescriptionLine.create(line.drugId(), line.quantity(), pricedItem.unitPrice(),
                    line.dosage(), pricedItem.drugName(), line.note()));
        }

        for (StockReservation old : allReservations) {
            if (old.isReserved() && requested.stream().noneMatch(line -> line.drugId().equals(old.getDrugId()))) {
                old.release(ReservationReleaseReason.PRESCRIPTION_EDITED, operation.actor().auditActorId(), now);
                reservations.save(old);
            }
        }
        for (PrescriptionLineRequest line : requested) {
            StockReservation old = byDrug.get(line.drugId());
            if (old == null) {
                reservations.save(StockReservation.create(line.drugId(), prescription.getPrescriptionId(),
                        line.quantity(), expiresAt));
            } else if (old.isReserved()) {
                old.reviseQuantity(line.quantity(), now);
                reservations.save(old);
            } else if (old.getStatus() == ReservationStatus.RELEASED
                    && old.getReleaseReason() == ReservationReleaseReason.PRESCRIPTION_EDITED) {
                old.renewAfterPrescriptionEdit(line.quantity(), now);
                reservations.save(old);
            } else {
                throw new StockReservationRuleException("RESERVATION_INVALID_TRANSITION",
                        "Only an unexpired hold released by an earlier edit can be restored");
            }
        }

        int previousRevision = prescription.getRevision();
        prescription.revise(operation.expectedRevision(), replacementLines, now);
        Prescription saved = prescriptions.save(prescription);
        var event = PrescriptionCareEventFactory.createRevision(UUID.randomUUID(), operation.correlationId(),
                saved, operation.operationId(), previousRevision, saved.getRevisionAt());
        careEvents.storeHeld(event);
        var applied = operation.transition(PrescriptionRevisionOperation.Status.APPLIED, null, null, now);
        operations.update(applied, PrescriptionRevisionOperation.Status.PREPARED);
        return applied;
    }

    @Transactional
    public PrescriptionRevisionOperation markCommitted(UUID operationId) {
        var operation = lock(operationId);
        if (operation.status() == PrescriptionRevisionOperation.Status.APPLIED) {
            return operations.update(operation.transition(PrescriptionRevisionOperation.Status.COMMITTED,
                    null, null, clock.instant()), PrescriptionRevisionOperation.Status.APPLIED);
        }
        return operation;
    }

    private PrescriptionRevisionOperation lock(UUID operationId) {
        return operations.findByIdForUpdate(operationId)
                .orElseThrow(() -> new PrescriptionRuleException("PRESCRIPTION_REVISION_NOT_FOUND", "Revision operation was not found"));
    }

    private static void requireSameIntent(PrescriptionRevisionOperation stored,
            UpdatePrescriptionCommand command, String fingerprint) {
        if (!stored.prescriptionId().equals(command.prescriptionId())
                || !stored.commandFingerprint().equals(fingerprint)) {
            throw new DuplicateResourceException("PRESCRIPTION_IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key was already used for a different prescription edit");
        }
    }

    private static void requireEditable(Prescription prescription, ActorIdentity actor, int expectedRevision) {
        var care = prescription.getCareContext();
        if (care.contractVersion() != CareContractVersion.V1 || care.careContext() != CareContext.OUTPATIENT
                || care.episode() == null || care.episode().type() != com.mediflow.pharmacy.domain.model.enums.CareEpisodeType.OUTPATIENT_VISIT
                || care.admissionId() != null || prescription.getRecordId() == null || care.priceCode() == null) {
            throw new PrescriptionRuleException("PHARMACY_OUTPATIENT_V1_REQUIRED",
                    "Only an exact outpatient V1 prescription can be edited");
        }
        if (prescription.getStatus() != PrescriptionStatus.ACTIVE) {
            throw new PrescriptionRuleException("PRESCRIPTION_NOT_EDITABLE", "Only an active prescription can be edited");
        }
        if (actor == null || (!actor.isAdministrator() && !"DOCTOR".equals(actor.role()))) {
            throw new ForbiddenOperationException("PRESCRIPTION_EDIT_FORBIDDEN", "Only the prescribing doctor or an authorized administrator may edit");
        }
        if (!actor.isAdministrator() && (actor.staffId() == null || !actor.staffId().equals(prescription.getDoctorId()))) {
            throw new ForbiddenOperationException("PRESCRIPTION_EDIT_FORBIDDEN", "Doctors may only edit prescriptions they authored");
        }
        actor.auditActorId();
        if (expectedRevision != prescription.getRevision()) {
            throw new DuplicateResourceException("PRESCRIPTION_REVISION_CONFLICT", "Prescription changed; reload before editing");
        }
    }

    private static void requireProof(OutpatientPrescriptionContextPort.Observation context,
            PrescriptionIdentityPort.Observation identity, Prescription prescription, Instant at) {
        if (context == null || identity == null) throw new com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException();
        context.requireExact(prescription.getRecordId(), prescription.getPatientId(), prescription.getDoctorId(),
                prescription.getDepartmentId(), prescription.getCareContext().episode().id(), at);
        identity.requireExact(prescription.getPatientId(), prescription.getDoctorId(), prescription.getDepartmentId(), at);
    }

    private static List<PrescriptionLineRequest> validateAndSort(List<PrescriptionLineRequest> lines) {
        if (lines == null || lines.isEmpty() || lines.size() > 100) {
            throw new PrescriptionRuleException("PRESCRIPTION_EMPTY", "Prescription must contain 1 to 100 lines");
        }
        Set<UUID> seen = new HashSet<>();
        for (var line : lines) {
            if (line == null || line.drugId() == null || line.quantity() == null || line.quantity() < 1
                    || (line.dosage() != null && line.dosage().length() > 255)
                    || (line.note() != null && line.note().length() > 500) || !seen.add(line.drugId())) {
                throw new PrescriptionRuleException("PRESCRIPTION_REVISION_LINES_INVALID",
                        "Positive unique drug lines with bounded instructions are required");
            }
        }
        return lines.stream().sorted(Comparator.comparing(PrescriptionLineRequest::drugId)).toList();
    }

    private static BigDecimal total(List<PrescriptionRevisionBillingPayload.Item> items) {
        return items.stream().map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static String fingerprint(UpdatePrescriptionCommand command) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            append(digest, command.prescriptionId());
            append(digest, command.request().expectedRevision());
            append(digest, command.actor().accountId());
            append(digest, command.actor().staffId());
            append(digest, command.actor().role());
            for (var line : command.request().lines().stream()
                    .sorted(Comparator.comparing(PrescriptionLineRequest::drugId)).toList()) {
                append(digest, line.drugId()); append(digest, line.quantity()); append(digest, line.dosage()); append(digest, line.note());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private static void append(MessageDigest digest, Object value) {
        byte[] bytes = value == null ? new byte[0] : value.toString().getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(4).putInt(value == null ? -1 : bytes.length).array());
        digest.update(bytes);
    }

    private static PrescriptionNotFoundException notFound(UUID id) {
        return new PrescriptionNotFoundException("Prescription not found: " + id);
    }
}
