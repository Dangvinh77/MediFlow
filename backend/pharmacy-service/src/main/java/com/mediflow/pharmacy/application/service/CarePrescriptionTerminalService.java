package com.mediflow.pharmacy.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.pharmacy.application.dto.command.CancelPrescriptionCommand;
import com.mediflow.pharmacy.application.dto.response.CancelPrescriptionResult;
import com.mediflow.pharmacy.application.event.carefinance.PrescriptionCareEvent.EventType;
import com.mediflow.pharmacy.application.mapper.PrescriptionCareEventFactory;
import com.mediflow.pharmacy.application.port.in.CancelCarePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.CapturePrescriptionCareEventUseCase;
import com.mediflow.pharmacy.application.port.in.ExpireCarePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.out.DispenseSlipRepositoryPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionCareEventWriterPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;
import com.mediflow.pharmacy.application.port.out.StockReservationRepositoryPort;
import com.mediflow.pharmacy.domain.exception.PrescriptionCancellationForbiddenException;
import com.mediflow.pharmacy.domain.exception.PrescriptionNotFoundException;
import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;
import com.mediflow.pharmacy.domain.model.DispenseSlip;
import com.mediflow.pharmacy.domain.model.Prescription;
import com.mediflow.pharmacy.domain.model.StockReservation;
import com.mediflow.pharmacy.domain.model.enums.CareContractVersion;
import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;
import com.mediflow.pharmacy.domain.model.enums.PrescriptionStatus;
import com.mediflow.pharmacy.domain.model.enums.ReservationReleaseReason;

/** P-03.5: reservation release, terminal business proof and HELD V1 fact commit together. */
@Service
@Transactional
public class CarePrescriptionTerminalService implements CancelCarePrescriptionUseCase, ExpireCarePrescriptionUseCase {
    private final PrescriptionRepositoryPort prescriptions;
    private final DispenseSlipRepositoryPort slips;
    private final StockReservationRepositoryPort reservations;
    private final CapturePrescriptionCareEventUseCase capture;
    private final PrescriptionCareEventWriterPort writer;
    private final Clock clock;

    public CarePrescriptionTerminalService(PrescriptionRepositoryPort prescriptions, DispenseSlipRepositoryPort slips,
            StockReservationRepositoryPort reservations, CapturePrescriptionCareEventUseCase capture,
            PrescriptionCareEventWriterPort writer, Clock clock) {
        this.prescriptions = prescriptions;
        this.slips = slips;
        this.reservations = reservations;
        this.capture = capture;
        this.writer = writer;
        this.clock = clock;
    }

    @Override
    public CancelPrescriptionResult cancelCare(CancelPrescriptionCommand command) {
        if (command == null || command.reason() == null || command.reason().isBlank()
                || command.reason().trim().length() > 500) throw invalid("A bounded cancellation reason is required");
        var actor = command.actor();
        if (!actor.isAdministrator() && (!"DOCTOR".equals(actor.role()) || actor.staffId() == null)) {
            throw forbidden();
        }
        var prescription = lock(command.prescriptionId());
        if (!actor.isAdministrator() && !actor.staffId().equals(prescription.getDoctorId())) throw forbidden();
        var slip = lockSlip(prescription);
        String reason = command.reason().trim();
        if (prescription.isCancelled()) {
            verifyTerminal(prescription, slip, EventType.CANCELLED, DispenseStatus.CANCELLED);
            if (!reason.equals(prescription.getCancellationReason())
                    || !actor.auditActorId().equals(prescription.getCancelledBy())) {
                throw invalid("Cancellation retry differs from the original actor/reason");
            }
            return result(prescription, 0);
        }
        requireActive(prescription, slip);
        var locked = lockReservations(prescription);
        Instant now = businessTime(prescription);
        var releaseReason = actor.isAdministrator() ? ReservationReleaseReason.ADMIN_OVERRIDE
                : ReservationReleaseReason.PRESCRIPTION_CANCELLED;
        for (var reservation : locked) {
            reservation.release(releaseReason, actor.auditActorId(), now);
            reservations.save(reservation);
        }
        prescription.cancel(actor.auditActorId(), reason, now);
        slip.markCancelled(reason, now);
        persistAndCapture(prescription, slip, EventType.CANCELLED, command.correlationId());
        return result(prescription, locked.size());
    }

    @Override
    public int expireCare(UUID prescriptionId, String correlationId) {
        if (correlationId == null || correlationId.isBlank()) throw invalid("Correlation is required");
        var prescription = lock(prescriptionId);
        var slip = lockSlip(prescription);
        if (prescription.getStatus() == PrescriptionStatus.EXPIRED) {
            verifyTerminal(prescription, slip, EventType.EXPIRED, DispenseStatus.EXPIRED);
            return 0;
        }
        requireActive(prescription, slip);
        var locked = lockReservations(prescription);
        Instant now = businessTime(prescription); // After every lock wait, not the batch discovery time.
        if (locked.stream().anyMatch(reservation -> !reservation.isExpiredAt(now))) return 0;
        for (var reservation : locked) {
            reservation.expire(now);
            reservations.save(reservation);
        }
        prescription.markExpired(now);
        slip.markExpired(now);
        persistAndCapture(prescription, slip, EventType.EXPIRED, correlationId);
        return locked.size();
    }

    private Prescription lock(UUID id) {
        if (id == null) throw invalid("Prescription identity is required");
        var prescription = prescriptions.findByIdForUpdate(id)
                .orElseThrow(() -> new PrescriptionNotFoundException("Prescription not found"));
        if (prescription.getCareContext().contractVersion() != CareContractVersion.V1) {
            throw invalid("Internal care lifecycle requires V1, never legacy reinterpretation");
        }
        return prescription;
    }

    private DispenseSlip lockSlip(Prescription prescription) {
        var slip = slips.findByPrescriptionForUpdate(prescription.getPrescriptionId())
                .orElseThrow(() -> invalid("Persisted dispense slip is required"));
        if (slip.getDispenseId() == null || !prescription.getPrescriptionId().equals(slip.getPrescriptionId())) {
            throw invalid("Dispense identity differs from the prescription");
        }
        return slip;
    }

    private static void requireActive(Prescription prescription, DispenseSlip slip) {
        if (!prescription.isActive() || !slip.isPending()) throw invalid("Only ACTIVE/PENDING orders may terminate");
    }

    private List<StockReservation> lockReservations(Prescription prescription) {
        var locked = reservations.findByPrescriptionForUpdate(prescription.getPrescriptionId());
        if (locked.size() != prescription.getLines().size() || locked.isEmpty()
                || locked.stream().map(StockReservation::getDrugId).distinct().count() != locked.size()) {
            throw invalid("Whole-order reservation evidence is required");
        }
        for (var line : prescription.getLines()) {
            if (locked.stream().noneMatch(reservation -> reservation.isReserved()
                    && prescription.getPrescriptionId().equals(reservation.getPrescriptionId())
                    && line.getDrugId().equals(reservation.getDrugId()) && line.getQuantity() == reservation.getQuantity())) {
                throw invalid("Reservation identity, quantity or state differs from the prescription");
            }
        }
        return locked;
    }

    private Instant businessTime(Prescription prescription) {
        Instant now = clock.instant();
        if (prescription.getCreatedAt() == null || now.isBefore(prescription.getCreatedAt())) {
            throw invalid("Business time cannot precede creation");
        }
        return now;
    }

    private void persistAndCapture(Prescription prescription, DispenseSlip slip, EventType type, String correlationId) {
        prescriptions.save(prescription);
        slips.save(slip);
        capture.capture(prescription.getPrescriptionId(), UUID.randomUUID(), type, correlationId);
    }

    private void verifyTerminal(Prescription prescription, DispenseSlip slip, EventType type, DispenseStatus status) {
        if (slip.getStatus() != status || prescription.getLifecycleAt() == null
                || !Objects.equals(prescription.getLifecycleAt(), slip.getLifecycleAt())) throw invalid("Terminal proof is inconsistent");
        String reason = type == EventType.CANCELLED ? prescription.getCancellationReason() : slip.getFailureReason();
        if (type == EventType.CANCELLED && !Objects.equals(reason, slip.getFailureReason())) throw invalid("Terminal reason is inconsistent");
        var held = writer.findHeld(prescription.getPrescriptionId(), type)
                .orElseThrow(() -> invalid("Held terminal proof is missing"));
        var expected = PrescriptionCareEventFactory.create(held.eventId(), type, held.correlationId(), prescription, null, reason);
        if (!expected.equals(held)) throw invalid("Held terminal proof differs from persisted evidence");
    }

    private static CancelPrescriptionResult result(Prescription prescription, int released) {
        return new CancelPrescriptionResult(prescription.getPrescriptionId(), prescription.getStatus(), released, prescription.getLifecycleAt());
    }
    private static PrescriptionRuleException invalid(String message) {
        return new PrescriptionRuleException("PHARMACY_CARE_LIFECYCLE_EVIDENCE_INVALID", message);
    }
    private static PrescriptionCancellationForbiddenException forbidden() {
        return new PrescriptionCancellationForbiddenException("Only the prescribing doctor or an administrator may cancel");
    }
}
