package com.mediflow.pharmacy.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.dto.command.CancelPrescriptionCommand;
import com.mediflow.pharmacy.application.event.carefinance.PrescriptionCareEvent;
import com.mediflow.pharmacy.application.port.out.*;
import com.mediflow.pharmacy.domain.model.*;
import com.mediflow.pharmacy.domain.model.enums.*;

class CarePrescriptionTerminalServiceTest {
    private final PrescriptionRepositoryPort prescriptions = mock(PrescriptionRepositoryPort.class);
    private final DispenseSlipRepositoryPort slips = mock(DispenseSlipRepositoryPort.class);
    private final StockReservationRepositoryPort reservations = mock(StockReservationRepositoryPort.class);
    private final PrescriptionCareEventWriterPort writer = mock(PrescriptionCareEventWriterPort.class);
    private final Clock clock = mock(Clock.class);
    private final Instant now = Instant.parse("2026-10-02T01:00:00.123456789Z");
    private final ActorIdentity doctor = new ActorIdentity(UUID.randomUUID(), UUID.randomUUID(), "DOCTOR");
    private Prescription prescription;
    private DispenseSlip slip;
    private StockReservation reservation;
    private CarePrescriptionTerminalService service;

    @BeforeEach void setup() {
        var line = PrescriptionLine.create(UUID.randomUUID(), 2, new BigDecimal("50.00"), "Daily", "Stored name");
        prescription = Prescription.restore(UUID.randomUUID(), null, UUID.randomUUID(), doctor.staffId(), UUID.randomUUID(),
                LocalDate.of(2026, 10, 2), new BigDecimal("100.00"), List.of(line), PrescriptionStatus.ACTIVE,
                null, null, null, now.minusSeconds(60), now.minusSeconds(60),
                PrescriptionCareContext.v1(CareContext.OUTPATIENT,
                        new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID()), null, "DRUG"));
        slip = DispenseSlip.restore(UUID.randomUUID(), prescription.getPrescriptionId(), DispenseStatus.PENDING,
                null, null, null, now.minusSeconds(60), now.minusSeconds(60));
        reservation = StockReservation.restore(UUID.randomUUID(), line.getDrugId(), prescription.getPrescriptionId(), 2,
                ReservationStatus.RESERVED, now.minusSeconds(60), now.plusSeconds(10), null, null, null, null);
        when(clock.instant()).thenReturn(now);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(prescriptions.findByIdForUpdate(prescription.getPrescriptionId())).thenReturn(Optional.of(prescription));
        when(slips.findByPrescriptionForUpdate(prescription.getPrescriptionId())).thenReturn(Optional.of(slip));
        when(reservations.findByPrescriptionForUpdate(prescription.getPrescriptionId())).thenReturn(List.of(reservation));
        doAnswer(call -> {
            PrescriptionCareEvent event = call.getArgument(0);
            when(writer.findHeld(event.payload().prescriptionId(), event.eventType())).thenReturn(Optional.of(event));
            return null;
        }).when(writer).storeHeld(any());
        service = new CarePrescriptionTerminalService(prescriptions, slips, reservations,
                new PrescriptionCareEventCaptureService(prescriptions, slips, writer), writer, clock);
    }

    @Test void cancel_ownedDoctor_releasesReservationAndStoresExactHeldProof() {
        var result = cancel(doctor, " Changed treatment ");
        assertThat(result.releasedReservations()).isOne();
        assertThat(result.cancelledAt()).isEqualTo(now);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(reservation.getReleasedBy()).isEqualTo(doctor.staffId());
        assertThat(prescription.getLifecycleAt()).isEqualTo(slip.getLifecycleAt()).isEqualTo(now);
        assertThat(prescription.getCancellationReason()).isEqualTo("Changed treatment");
        verify(writer).storeHeld(argThat(event -> event.eventType() == PrescriptionCareEvent.EventType.CANCELLED
                && event.occurredAt().equals(now) && event.payload().dispenseId() == null));
    }
    @Test void cancel_sameActorAndReasonRetry_doesNotReleaseOrWriteAgain() {
        cancel(doctor, "Changed treatment");
        assertThat(cancel(doctor, "Changed treatment").releasedReservations()).isZero();
        verify(reservations, times(1)).save(any());
        verify(writer, times(1)).storeHeld(any());
    }
    @Test void cancel_changedRetryReason_conflictsWithoutSecondEffect() {
        cancel(doctor, "First");
        assertThatThrownBy(() -> cancel(doctor, "Second")).hasMessageContaining("retry differs");
        verify(reservations, times(1)).save(any());
    }
    @Test void cancel_wrongDoctor_deniesBeforeSlipOrReservationAccess() {
        assertThatThrownBy(() -> cancel(new ActorIdentity(UUID.randomUUID(), UUID.randomUUID(), "DOCTOR"), "Reason"));
        verifyNoInteractions(slips, reservations, writer);
    }
    @Test void cancel_systemWithForgedStaffRole_deniesBeforeAggregateLookup() {
        assertThatThrownBy(() -> cancel(new ActorIdentity(UUID.randomUUID(), doctor.staffId(), "SYSTEM"), "Reason"));
        verifyNoInteractions(prescriptions, slips, reservations, writer);
    }
    @Test void cancel_missingWholeReservation_rejectsBeforeMutation() {
        when(reservations.findByPrescriptionForUpdate(prescription.getPrescriptionId())).thenReturn(List.of());
        assertThatThrownBy(() -> cancel(doctor, "Reason")).hasMessageContaining("Whole-order");
        assertThat(prescription.isActive()).isTrue();
        verify(writer, never()).storeHeld(any());
    }
    @Test void cancel_missingHeldProofOnRetry_rejectsFalseSuccess() {
        cancel(doctor, "Reason");
        when(writer.findHeld(prescription.getPrescriptionId(), PrescriptionCareEvent.EventType.CANCELLED)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> cancel(doctor, "Reason")).hasMessageContaining("proof is missing");
    }
    @Test void expiry_notAllExpired_leavesEntireOrderActive() {
        assertThat(service.expireCare(prescription.getPrescriptionId(), "expiry")).isZero();
        assertThat(reservation.isReserved()).isTrue();
        verify(writer, never()).storeHeld(any());
    }
    @Test void expiry_waitCrossesBoundary_usesClockAfterLocksThenReplayNoOp() {
        when(reservations.findByPrescriptionForUpdate(prescription.getPrescriptionId())).thenAnswer(call -> {
            when(clock.instant()).thenReturn(reservation.getExpiresAt());
            return List.of(reservation);
        });
        assertThat(service.expireCare(prescription.getPrescriptionId(), "expiry")).isOne();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(prescription.getLifecycleAt()).isEqualTo(reservation.getExpiresAt());
        assertThat(service.expireCare(prescription.getPrescriptionId(), "retry")).isZero();
        verify(writer, times(1)).storeHeld(any());
    }
    @Test void expiry_fulfilledOrder_neverReleasesDispensedStock() {
        prescription.markFulfilled(now);
        assertThatThrownBy(() -> service.expireCare(prescription.getPrescriptionId(), "expiry"));
        verifyNoInteractions(reservations, writer);
    }
    private com.mediflow.pharmacy.application.dto.response.CancelPrescriptionResult cancel(ActorIdentity actor, String reason) {
        return service.cancelCare(new CancelPrescriptionCommand(prescription.getPrescriptionId(), actor, reason, "cancel"));
    }
}
