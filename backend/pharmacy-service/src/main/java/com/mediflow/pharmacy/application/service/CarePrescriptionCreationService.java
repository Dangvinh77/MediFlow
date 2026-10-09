package com.mediflow.pharmacy.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.pharmacy.application.dto.command.CreatePrescriptionCommand;
import com.mediflow.pharmacy.application.dto.request.PrescriptionLineRequest;
import com.mediflow.pharmacy.application.event.carefinance.PrescriptionCareEvent.EventType;
import com.mediflow.pharmacy.application.port.in.CapturePrescriptionCareEventUseCase;
import com.mediflow.pharmacy.application.port.in.CreateCarePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.out.*;
import com.mediflow.pharmacy.domain.exception.PrescriptionCreationForbiddenException;
import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;
import com.mediflow.pharmacy.domain.model.*;
import com.mediflow.pharmacy.domain.model.enums.CareContext;

/** P-02.5: server price/name snapshots, stock reservations and held creation in one transaction. */
@Service
@Transactional
public class CarePrescriptionCreationService implements CreateCarePrescriptionUseCase,
        com.mediflow.pharmacy.application.port.in.CreateCarePrescriptionWithContextUseCase {
    private final CarePrescriptionCreationPort receipts;
    private final DrugRepositoryPort drugs;
    private final PrescriptionRepositoryPort prescriptions;
    private final DispenseSlipRepositoryPort slips;
    private final StockReservationRepositoryPort reservations;
    private final CapturePrescriptionCareEventUseCase capture;
    private final Clock clock;
    private final Duration reservationTtl;

    public CarePrescriptionCreationService(CarePrescriptionCreationPort receipts, DrugRepositoryPort drugs,
            PrescriptionRepositoryPort prescriptions, DispenseSlipRepositoryPort slips,
            StockReservationRepositoryPort reservations, CapturePrescriptionCareEventUseCase capture,
            Clock clock, Duration reservationTtl) {
        if (reservationTtl == null || reservationTtl.isNegative() || reservationTtl.isZero()) {
            throw new IllegalArgumentException("Positive reservation TTL is required");
        }
        this.receipts = receipts;
        this.drugs = drugs;
        this.prescriptions = prescriptions;
        this.slips = slips;
        this.reservations = reservations;
        this.capture = capture;
        this.clock = clock;
        this.reservationTtl = reservationTtl;
    }

    @Override
    public UUID createCare(UUID commandId, CreatePrescriptionCommand command) {
        return createInternal(commandId, command, null, null);
    }

    @Override
    public UUID createCare(UUID commandId, CreatePrescriptionCommand command, OutpatientPrescriptionContextPort.Observation context) {
        if (context == null) throw invalid("Clinical context proof is required");
        return createInternal(commandId, command, context, null);
    }

    @Override
    public UUID createCare(UUID commandId, CreatePrescriptionCommand command, OutpatientPrescriptionContextPort.Observation context,
            PrescriptionIdentityPort.Observation identities) {
        if (context == null || identities == null) throw invalid("Clinical and identity proofs are required");
        return createInternal(commandId, command, context, identities);
    }

    private UUID createInternal(UUID commandId, CreatePrescriptionCommand command, OutpatientPrescriptionContextPort.Observation context,
            PrescriptionIdentityPort.Observation identities) {
        validate(commandId, command);
        var request = command.request();
        requireContext(context, command, clock.instant());
        requireIdentities(identities, command, clock.instant());
        // Authorization precedes receipt replay; correlation is delivery metadata, not business intent.
        var existing = receipts.claim(commandId, command.actor().accountId(), fingerprint(command));
        requireContext(context, command, clock.instant()); // Receipt lock/replay can also wait.
        requireIdentities(identities, command, clock.instant());
        if (existing.isPresent()) return existing.get();
        var requested = request.lines().stream().sorted(Comparator.comparing(PrescriptionLineRequest::drugId)).toList();
        var locked = requested.stream().map(line -> drugs.findByIdForUpdate(line.drugId())
                .orElseThrow(() -> invalid("Requested drug is unavailable"))).toList();
        var now = clock.instant(); // All drug lock waits have completed.
        requireContext(context, command, now);
        requireIdentities(identities, command, now);
        LocalDate today = now.atZone(clock.getZone()).toLocalDate();
        if (request.prescribedDate().isAfter(today)) throw invalid("Prescription date cannot be in the future");
        var lines = new java.util.ArrayList<PrescriptionLine>();
        for (int index = 0; index < requested.size(); index++) {
            var line = requested.get(index);
            var drug = locked.get(index);
            if (!drug.getDrugId().equals(line.drugId()) || drug.getExpiryDate().isBefore(today)) {
                throw invalid("Drug identity/expiry is invalid");
            }
            long reserved = reservations.findReservedByDrug(line.drugId()).stream()
                    .mapToLong(StockReservation::getQuantity).sum();
            if ((long) drug.getStockQuantity() - reserved < line.quantity()) throw invalid("Insufficient available stock");
            lines.add(PrescriptionLine.create(drug.getDrugId(), line.quantity(), drug.getPrice(), line.dosage(), drug.getDrugName()));
        }
        now = clock.instant(); // Reservation reads/waits must not retain the earlier business date.
        requireContext(context, command, now);
        requireIdentities(identities, command, now);
        today = now.atZone(clock.getZone()).toLocalDate();
        if (request.prescribedDate().isAfter(today)) throw invalid("Prescription date cannot be in the future");
        for (var drug : locked) {
            if (drug.getExpiryDate().isBefore(today)) throw invalid("Drug identity/expiry is invalid");
        }
        var prescription = prescriptions.save(Prescription.create(request.recordId(), request.patientId(), request.doctorId(),
                request.departmentId(), request.prescribedDate(), lines, PrescriptionCareContext.v1(request.careContext(),
                        new CareEpisode(request.careEpisodeType(), request.careEpisodeId()), null, request.priceCode())));
        // Creation time comes from the persisted Rx; TTL cannot precede that server business proof.
        var expiresAt = (prescription.getCreatedAt().isAfter(now) ? prescription.getCreatedAt() : now).plus(reservationTtl);
        for (var line : lines) reservations.save(StockReservation.create(line.getDrugId(), prescription.getPrescriptionId(), line.getQuantity(), expiresAt));
        slips.save(DispenseSlip.createPending(prescription.getPrescriptionId()));
        capture.capture(prescription.getPrescriptionId(), UUID.randomUUID(), EventType.CREATED, command.correlationId());
        receipts.complete(commandId, prescription.getPrescriptionId());
        return prescription.getPrescriptionId();
    }

    static void validate(UUID commandId, CreatePrescriptionCommand command) {
        if (commandId == null || command == null) throw invalid("Command identity is required");
        var request = command.request();
        var actor = command.actor();
        if (!actor.isAdministrator() && (!"DOCTOR".equals(actor.role()) || actor.staffId() == null
                || !actor.staffId().equals(request.doctorId()))) {
            throw new PrescriptionCreationForbiddenException("Only an authenticated prescribing doctor or administrator may create");
        }
        if (!Integer.valueOf(1).equals(request.careContractVersion()) || !request.isCareContractValid()
                || request.patientId() == null || request.doctorId() == null || request.departmentId() == null
                || request.prescribedDate() == null || request.lines() == null || request.lines().isEmpty()) {
            throw invalid("Complete V1 care intent is required");
        }
        if (request.careContext() != CareContext.OUTPATIENT) {
            throw invalid("Admission creation awaits authoritative medication eligibility, never active=true inference");
        }
        if (request.lines().stream().anyMatch(line -> line == null || line.drugId() == null || line.quantity() == null
                || line.quantity() < 1 || (line.dosage() != null && line.dosage().length() > 255))
                || request.lines().stream().map(PrescriptionLineRequest::drugId).distinct().count() != request.lines().size()) {
            throw invalid("Positive, unique, bounded prescription lines are required");
        }
    }

    private static void requireContext(OutpatientPrescriptionContextPort.Observation context,
            CreatePrescriptionCommand command, java.time.Instant at) {
        if (context == null) return; // Existing internal kernel; never selected as a public V1 fallback.
        var request = command.request();
        context.requireExact(request.recordId(), request.patientId(), request.doctorId(), request.departmentId(),
                request.careEpisodeId(), at);
    }

    private static void requireIdentities(PrescriptionIdentityPort.Observation identities, CreatePrescriptionCommand command,
            java.time.Instant at) {
        if (identities == null) return; // Trusted low-level kernels are not public fallback paths.
        var request = command.request();
        identities.requireExact(request.patientId(), request.doctorId(), request.departmentId(), at);
    }

    private static String fingerprint(CreatePrescriptionCommand command) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var request = command.request();
            for (Object value : List.of(command.actor().role(), request.patientId(), request.doctorId(), request.departmentId(),
                    request.prescribedDate(), request.careContractVersion(), request.careContext(), request.careEpisodeType(),
                    request.careEpisodeId(), request.priceCode())) append(digest, value);
            append(digest, command.actor().staffId());
            append(digest, request.recordId());
            for (var line : request.lines().stream().sorted(Comparator.comparing(PrescriptionLineRequest::drugId)).toList()) {
                append(digest, line.drugId()); append(digest, line.quantity()); append(digest, line.dosage());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
    private static void append(MessageDigest digest, Object value) {
        byte[] bytes = value == null ? new byte[0] : value.toString().getBytes(StandardCharsets.UTF_8);
        digest.update(java.nio.ByteBuffer.allocate(4).putInt(value == null ? -1 : bytes.length).array());
        digest.update(bytes);
    }
    private static PrescriptionRuleException invalid(String message) {
        return new PrescriptionRuleException("PHARMACY_CARE_CREATION_INVALID", message);
    }
}
