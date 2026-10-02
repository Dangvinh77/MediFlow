package com.mediflow.pharmacy.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

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
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;

import com.mediflow.pharmacy.application.event.carefinance.PrescriptionCareEvent;
import com.mediflow.pharmacy.application.mapper.DispenseDtoMapper;
import com.mediflow.pharmacy.application.port.out.*;
import com.mediflow.pharmacy.domain.exception.DispenseAuthorizationException;
import com.mediflow.pharmacy.domain.model.*;
import com.mediflow.pharmacy.domain.model.enums.*;
import com.mediflow.pharmacy.support.ClearanceTestFixtures;

class CareDispenseTransactionServiceTest {
    private final PrescriptionRepositoryPort prescriptions = mock(PrescriptionRepositoryPort.class);
    private final DispenseSlipRepositoryPort slips = mock(DispenseSlipRepositoryPort.class);
    private final DrugRepositoryPort drugs = mock(DrugRepositoryPort.class);
    private final StockReservationRepositoryPort reservations = mock(StockReservationRepositoryPort.class);
    private final PrescriptionClearancePort clearances = mock(PrescriptionClearancePort.class);
    private final PrescriptionCareEventWriterPort writer = mock(PrescriptionCareEventWriterPort.class);
    private final PharmacyEventPublisherPort events = mock(PharmacyEventPublisherPort.class);
    private final Clock clock = mock(Clock.class);
    private final Instant now = ClearanceTestFixtures.GRANTED_AT.plusSeconds(60);
    private PrescriptionClearance grant;
    private Prescription prescription;
    private DispenseSlip slip;
    private Drug drug;
    private StockReservation reservation;
    private CareDispenseTransactionService service;

    @BeforeEach
    void setup() {
        when(clock.instant()).thenReturn(now);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        grant = ClearanceTestFixtures.grant();
        var base = ClearanceTestFixtures.prescription(grant);
        var line = PrescriptionLine.create(UUID.randomUUID(), 2, new BigDecimal("50.00"), "Daily", "Historical name");
        prescription = Prescription.restore(base.getPrescriptionId(), base.getRecordId(), base.getPatientId(),
                base.getDoctorId(), base.getDepartmentId(), base.getPrescribedDate(), base.getTotalAmount(), List.of(line),
                PrescriptionStatus.ACTIVE, null, null, null, base.getCreatedAt(), base.getUpdatedAt(), base.getCareContext());
        slip = DispenseSlip.restore(UUID.randomUUID(), prescription.getPrescriptionId(), DispenseStatus.PENDING,
                null, null, null, now.minusSeconds(30), now.minusSeconds(30));
        drug = Drug.restore(line.getDrugId(), "Current catalogue name", null, "tablet", new BigDecimal("999.00"),
                3, LocalDate.of(2027, 1, 1), null, 1, now, now);
        reservation = reservation(line.getDrugId(), now.plusSeconds(120));
        wire();
        service = new CareDispenseTransactionService(prescriptions, slips, drugs, reservations,
                new PrescriptionClearanceAuthorizationService(clearances, clock),
                new PrescriptionCareEventCaptureService(prescriptions, slips, writer), writer, events,
                Mappers.getMapper(DispenseDtoMapper.class), clock);
        doAnswer(call -> {
            PrescriptionCareEvent event = call.getArgument(0);
            when(writer.findHeld(event.payload().prescriptionId(), event.eventType())).thenReturn(Optional.of(event));
            return null;
        }).when(writer).storeHeld(any());
    }

    @Test
    void execute_exactClearance_writesHeldSnapshotAndSameBusinessTimeWithoutLegacyFilled() {
        var actor = DispenseActor.account(UUID.randomUUID());
        var result = execute(actor);
        assertThat(result.status()).isEqualTo(DispenseStatus.DISPENSED);
        assertThat(result.dispensedActorType()).isEqualTo(DispenseActorType.ACCOUNT);
        assertThat(result.dispensedBy()).isEqualTo(actor.id());
        assertThat(drug.getStockQuantity()).isOne();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.FULFILLED);
        assertThat(prescription.getLifecycleAt()).isEqualTo(now).isEqualTo(slip.getLifecycleAt());
        var captured = ArgumentCaptor.forClass(PrescriptionCareEvent.class);
        verify(writer).storeHeld(captured.capture());
        assertThat(captured.getValue().occurredAt()).isEqualTo(now);
        assertThat(captured.getValue().payload().dispenseId()).isEqualTo(slip.getDispenseId());
        assertThat(captured.getValue().payload().items().getFirst().drugName()).isEqualTo("Historical name");
        assertThat(captured.getValue().payload().items().getFirst().unitPrice()).isEqualByComparingTo("50.00");
        verify(events, never()).publishPrescriptionFilled(any());
        verify(events, never()).publishPrescriptionDispenseFailed(any());
        verify(events).publishStockLow(any());
    }

    @Test
    void execute_retry_returnsExistingProofWithoutStockOrSecondEvent() {
        var first = execute(DispenseActor.staff(UUID.randomUUID()));
        when(clock.instant()).thenReturn(grant.expiresAt().plusSeconds(1));
        var second = execute(DispenseActor.account(UUID.randomUUID()));
        assertThat(second).isEqualTo(first);
        assertThat(drug.getStockQuantity()).isOne();
        verify(drugs, times(1)).save(any());
        verify(writer, times(1)).storeHeld(any());
        verify(clearances, times(1)).markVerified(grant.clearanceId());
    }

    @Test
    void execute_terminalStateWithoutHeldProof_doesNotReportFalseSuccess() {
        execute(DispenseActor.staff(UUID.randomUUID()));
        when(writer.findHeld(grant.prescriptionId(), PrescriptionCareEvent.EventType.FILLED)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> execute(DispenseActor.staff(UUID.randomUUID()))).hasMessageContaining("proof is missing");
        assertThat(drug.getStockQuantity()).isOne();
        verify(drugs, times(1)).save(any());
    }

    @Test
    void execute_missingClearance_neverMutatesOrCompensates() {
        when(clearances.findByTargetForUpdate(grant.prescriptionId())).thenReturn(List.of());
        assertThatThrownBy(() -> execute(DispenseActor.staff(UUID.randomUUID())))
                .isInstanceOf(DispenseAuthorizationException.class);
        unchanged();
    }

    @Test
    void execute_clearanceExpiresWhileWaitingForStock_deniesUsingFreshTime() {
        when(drugs.findByIdForUpdate(drug.getDrugId())).thenAnswer(call -> {
            when(clock.instant()).thenReturn(grant.expiresAt());
            return Optional.of(drug);
        });
        assertThatThrownBy(() -> execute(DispenseActor.staff(UUID.randomUUID())))
                .isInstanceOf(DispenseAuthorizationException.class);
        verify(clearances, never()).markVerified(any());
        unchanged();
    }

    @Test
    void execute_clearanceExpiresWhileVerificationWrites_deniesBeforeStockEffect() {
        doAnswer(call -> { when(clock.instant()).thenReturn(grant.expiresAt()); return null; })
                .when(clearances).markVerified(grant.clearanceId());
        assertThatThrownBy(() -> execute(DispenseActor.staff(UUID.randomUUID())))
                .isInstanceOf(DispenseAuthorizationException.class);
        unchanged(); // Verification write itself is rolled back by the real transaction.
    }

    @Test
    void execute_reservationExpiresDuringClearanceLock_checksAgainAfterAllLocks() {
        doAnswer(call -> { when(clock.instant()).thenReturn(reservation.getExpiresAt()); return null; })
                .when(clearances).lockTarget(grant.prescriptionId());
        assertThatThrownBy(() -> execute(DispenseActor.staff(UUID.randomUUID()))).hasMessageContaining("reservation expired");
        unchanged();
    }

    @Test
    void execute_drugExpiresWhileWaitingAcrossMidnight_deniesWholeOrder() {
        drug = Drug.restore(drug.getDrugId(), drug.getDrugName(), null, "tablet", BigDecimal.ONE,
                3, LocalDate.of(2026, 10, 1), null, 1, now, now);
        var noExpiry = new PrescriptionClearance(grant.clearanceId(), grant.invoiceId(), grant.accountId(),
                grant.prescriptionId(), grant.patientId(), grant.episode(), grant.amount(), grant.currency(), grant.paymentMethod(),
                grant.grantedAt(), null, grant.payloadFingerprint());
        reservation = reservation(drug.getDrugId(), Instant.parse("2026-10-03T00:00:00Z"));
        wire();
        when(clearances.findByTargetForUpdate(grant.prescriptionId())).thenReturn(List.of(noExpiry));
        when(clock.instant()).thenReturn(Instant.parse("2026-10-02T00:00:00Z"));
        assertThatThrownBy(() -> execute(DispenseActor.staff(UUID.randomUUID()))).hasMessageContaining("Drug has expired");
        unchanged();
    }

    @Test
    void execute_incompleteReservationSet_rejectsBeforeAuthorization() {
        when(reservations.findByPrescription(grant.prescriptionId())).thenReturn(List.of());
        assertThatThrownBy(() -> execute(DispenseActor.staff(UUID.randomUUID()))).hasMessageContaining("Exact complete");
        verify(clearances, never()).lockTarget(any());
        unchanged();
    }

    @Test
    void execute_systemActor_cannotAutoDispenseOnGrant() {
        assertThatThrownBy(() -> execute(DispenseActor.system())).isInstanceOf(DispenseAuthorizationException.class);
        verify(prescriptions, never()).findByIdForUpdate(any());
        unchanged();
    }

    @Test
    void execute_legacyOrAdmission_contextCannotUseOutpatientExecutor() {
        UUID admissionId = UUID.randomUUID();
        for (var care : List.of(PrescriptionCareContext.legacy(), PrescriptionCareContext.v1(CareContext.ADMISSION,
                new CareEpisode(CareEpisodeType.ADMISSION, admissionId), admissionId, "DRUG"))) {
            var unsupported = Prescription.restore(grant.prescriptionId(), prescription.getRecordId(), grant.patientId(),
                    prescription.getDoctorId(), prescription.getDepartmentId(), prescription.getPrescribedDate(), prescription.getTotalAmount(),
                    prescription.getLines(), PrescriptionStatus.ACTIVE, null, null, null, grant.grantedAt(), grant.grantedAt(), care);
            when(prescriptions.findByIdForUpdate(grant.prescriptionId())).thenReturn(Optional.of(unsupported));
            assertThatThrownBy(() -> execute(DispenseActor.staff(UUID.randomUUID()))).isInstanceOf(DispenseAuthorizationException.class);
        }
        verify(slips, never()).findByPrescriptionForUpdate(any());
        unchanged();
    }

    @Test
    void execute_multiLineStockFailure_validatesWholeSetBeforeFirstMutation() {
        UUID firstId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID secondId = UUID.fromString("00000000-0000-4000-8000-000000000002");
        var lines = List.of(PrescriptionLine.create(firstId, 1, BigDecimal.TEN, "Daily", "First"),
                PrescriptionLine.create(secondId, 1, BigDecimal.TEN, "Daily", "Second"));
        prescription = Prescription.restore(grant.prescriptionId(), prescription.getRecordId(), grant.patientId(),
                prescription.getDoctorId(), prescription.getDepartmentId(), prescription.getPrescribedDate(), new BigDecimal("20.00"),
                lines.reversed(), PrescriptionStatus.ACTIVE, null, null, null, grant.grantedAt(), grant.grantedAt(), prescription.getCareContext());
        var first = Drug.restore(firstId, "First", null, "tablet", BigDecimal.TEN, 5, LocalDate.of(2027, 1, 1), null, 0, now, now);
        var second = Drug.restore(secondId, "Second", null, "tablet", BigDecimal.TEN, 0, LocalDate.of(2027, 1, 1), null, 0, now, now);
        var r1 = StockReservation.restore(UUID.randomUUID(), firstId, grant.prescriptionId(), 1, ReservationStatus.RESERVED,
                now, now.plusSeconds(120), now, null, null, null);
        var r2 = StockReservation.restore(UUID.randomUUID(), secondId, grant.prescriptionId(), 1, ReservationStatus.RESERVED,
                now, now.plusSeconds(120), now, null, null, null);
        when(prescriptions.findByIdForUpdate(grant.prescriptionId())).thenReturn(Optional.of(prescription));
        when(reservations.findByPrescription(grant.prescriptionId())).thenReturn(List.of(r2, r1));
        when(drugs.findByIdForUpdate(firstId)).thenReturn(Optional.of(first));
        when(drugs.findByIdForUpdate(secondId)).thenReturn(Optional.of(second));
        when(reservations.findReservedByPrescriptionForUpdate(grant.prescriptionId(), firstId)).thenReturn(Optional.of(r1));
        when(reservations.findReservedByPrescriptionForUpdate(grant.prescriptionId(), secondId)).thenReturn(Optional.of(r2));
        assertThatThrownBy(() -> execute(DispenseActor.staff(UUID.randomUUID()))).hasMessageContaining("Insufficient stock");
        assertThat(first.getStockQuantity()).isEqualTo(5);
        assertThat(r1.isReserved()).isTrue();
        verify(drugs, never()).save(any());
        verify(writer, never()).storeHeld(any());
        var order = inOrder(drugs, clearances);
        order.verify(drugs).findByIdForUpdate(firstId);
        order.verify(drugs).findByIdForUpdate(secondId);
        order.verify(clearances).lockTarget(grant.prescriptionId());
    }

    @Test void failure_definiteStockShortage_releasesWithoutDecrementAndRequiresHeldProofOnRetry() {
        drug = Drug.restore(drug.getDrugId(), drug.getDrugName(), null, "tablet", BigDecimal.ONE, 1,
                LocalDate.of(2027, 1, 1), null, 0, now, now);
        wire();
        var result = service.recordStockFailure(grant.prescriptionId(), DispenseActor.staff(UUID.randomUUID()), "failure");
        assertThat(result.orElseThrow().status()).isEqualTo(DispenseStatus.FAILED);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.DISPENSE_FAILED);
        assertThat(drug.getStockQuantity()).isOne();
        verify(drugs, never()).save(any());
        verifyNoInteractions(events);
        assertThat(service.recordStockFailure(grant.prescriptionId(), DispenseActor.account(UUID.randomUUID()), "retry")).isEqualTo(result);
        verify(writer, times(1)).storeHeld(any());
    }

    @Test void failure_stockNowHealthy_doesNotApplyStaleFailureOrDispense() {
        assertThat(service.recordStockFailure(grant.prescriptionId(), DispenseActor.staff(UUID.randomUUID()), "retry")).isEmpty();
        unchanged();
        verifyNoInteractions(clearances);
    }

    @Test void failure_missingAuthorization_evenWithDefiniteStockFailureNeverTerminates() {
        reservation = reservation(drug.getDrugId(), now.minusSeconds(1));
        wire();
        when(clearances.findByTargetForUpdate(grant.prescriptionId())).thenReturn(List.of());
        assertThatThrownBy(() -> service.recordStockFailure(grant.prescriptionId(), DispenseActor.staff(UUID.randomUUID()), "failure"))
                .isInstanceOf(DispenseAuthorizationException.class);
        unchanged();
    }

    @Test void failure_missingReservationEvidence_cannotBeMisclassifiedAsStockFailure() {
        when(reservations.findByPrescription(grant.prescriptionId())).thenReturn(List.of());
        assertThatThrownBy(() -> service.recordStockFailure(grant.prescriptionId(), DispenseActor.staff(UUID.randomUUID()), "failure"));
        unchanged();
        verifyNoInteractions(clearances);
    }

    private com.mediflow.pharmacy.application.dto.response.DispenseDTO execute(DispenseActor actor) {
        return service.execute(grant.prescriptionId(), actor, "care-command");
    }
    private StockReservation reservation(UUID drugId, Instant expiry) {
        return StockReservation.restore(UUID.randomUUID(), drugId, grant.prescriptionId(), 2,
                ReservationStatus.RESERVED, now, expiry, now, null, null, null);
    }
    private void wire() {
        when(prescriptions.findByIdForUpdate(grant.prescriptionId())).thenReturn(Optional.of(prescription));
        when(slips.findByPrescriptionForUpdate(grant.prescriptionId())).thenReturn(Optional.of(slip));
        when(slips.save(slip)).thenAnswer(call -> call.getArgument(0));
        when(prescriptions.save(prescription)).thenAnswer(call -> call.getArgument(0));
        when(drugs.findByIdForUpdate(drug.getDrugId())).thenReturn(Optional.of(drug));
        when(reservations.findByPrescription(grant.prescriptionId())).thenReturn(List.of(reservation));
        when(reservations.findReservedByPrescriptionForUpdate(grant.prescriptionId(), drug.getDrugId())).thenReturn(Optional.of(reservation));
        when(clearances.findByTargetForUpdate(grant.prescriptionId())).thenReturn(List.of(grant));
    }
    private void unchanged() {
        assertThat(drug.getStockQuantity()).isEqualTo(3);
        assertThat(reservation.isReserved()).isTrue();
        assertThat(prescription.isActive()).isTrue();
        assertThat(slip.isPending()).isTrue();
        verify(drugs, never()).save(any());
        verify(reservations, never()).save(any());
        verify(writer, never()).storeHeld(any());
        verifyNoInteractions(events);
    }
}
