package com.mediflow.pharmacy.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.mediflow.pharmacy.application.dto.command.*;
import com.mediflow.pharmacy.application.dto.request.*;
import com.mediflow.pharmacy.application.event.carefinance.PrescriptionCareEvent.EventType;
import com.mediflow.pharmacy.application.port.in.CapturePrescriptionCareEventUseCase;
import com.mediflow.pharmacy.application.port.out.*;
import com.mediflow.pharmacy.domain.model.*;
import com.mediflow.pharmacy.domain.model.enums.*;

class CarePrescriptionCreationServiceTest {
    private final CarePrescriptionCreationPort receipts = mock(CarePrescriptionCreationPort.class);
    private final DrugRepositoryPort drugs = mock(DrugRepositoryPort.class);
    private final PrescriptionRepositoryPort prescriptions = mock(PrescriptionRepositoryPort.class);
    private final DispenseSlipRepositoryPort slips = mock(DispenseSlipRepositoryPort.class);
    private final StockReservationRepositoryPort reservations = mock(StockReservationRepositoryPort.class);
    private final CapturePrescriptionCareEventUseCase capture = mock(CapturePrescriptionCareEventUseCase.class);
    private final Clock clock = mock(Clock.class);
    private final Instant now = Instant.parse("2026-10-02T01:00:00Z");
    private final ActorIdentity doctor = new ActorIdentity(UUID.randomUUID(), UUID.randomUUID(), "DOCTOR");
    private final UUID key = UUID.randomUUID();
    private final UUID resultId = UUID.randomUUID();
    private final UUID drugId = UUID.randomUUID();
    private final UUID patient = UUID.randomUUID();
    private final UUID department = UUID.randomUUID();
    private final UUID episode = UUID.randomUUID();
    private CarePrescriptionCreationService service;

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(now);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(receipts.claim(eq(key), eq(doctor.accountId()), anyString())).thenReturn(Optional.empty());
        when(drugs.findByIdForUpdate(drugId)).thenReturn(Optional.of(Drug.restore(drugId, "Server name", null, "tablet",
                new BigDecimal("50.00"), 5, LocalDate.of(2027, 1, 1), null, 1, now, now)));
        when(reservations.findReservedByDrug(drugId)).thenReturn(List.of());
        when(prescriptions.save(any())).thenAnswer(call -> {
            Prescription p = call.getArgument(0);
            return Prescription.restore(resultId, p.getRecordId(), p.getPatientId(), p.getDoctorId(), p.getDepartmentId(),
                    p.getPrescribedDate(), p.getTotalAmount(), p.getLines(), PrescriptionStatus.ACTIVE, null, null, null,
                    now, now, p.getCareContext());
        });
        service = new CarePrescriptionCreationService(receipts, drugs, prescriptions, slips, reservations, capture, clock, Duration.ofHours(24));
    }
    @Test void create_exactIntent_snapshotsServerPriceReservesAndCapturesHeldCreation() {
        assertThat(service.createCare(key, command(doctor, CareContext.OUTPATIENT, 2, "first"))).isEqualTo(resultId);
        verify(prescriptions).save(argThat(p -> p.getTotalAmount().compareTo(new BigDecimal("100.00")) == 0
                && p.getLines().getFirst().getDrugNameSnapshot().equals("Server name")));
        verify(reservations).save(argThat(r -> r.getPrescriptionId().equals(resultId) && r.getQuantity() == 2
                && r.getExpiresAt().equals(now.plus(Duration.ofHours(24)))));
        verify(capture).capture(eq(resultId), any(UUID.class), eq(EventType.CREATED), eq("first"));
        verify(receipts).complete(key, resultId);
    }
    @Test void create_sameCommandReplay_returnsOriginalWithoutCatalogueOrEventEffects() {
        when(receipts.claim(eq(key), eq(doctor.accountId()), anyString())).thenReturn(Optional.of(resultId));
        assertThat(service.createCare(key, command(doctor, CareContext.OUTPATIENT, 2, "retry"))).isEqualTo(resultId);
        verifyNoInteractions(drugs, prescriptions, slips, reservations, capture);
    }
    @Test void create_correlationChanges_keepSameBusinessFingerprint() {
        service.createCare(key, command(doctor, CareContext.OUTPATIENT, 2, "first"));
        service.createCare(key, command(doctor, CareContext.OUTPATIENT, 2, "different-correlation"));
        var fingerprints = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(receipts, times(2)).claim(eq(key), eq(doctor.accountId()), fingerprints.capture());
        assertThat(fingerprints.getAllValues().get(0)).isEqualTo(fingerprints.getAllValues().get(1));
    }
    @Test void create_changedQuantity_changesBusinessFingerprint() {
        service.createCare(key, command(doctor, CareContext.OUTPATIENT, 2, "a"));
        service.createCare(key, command(doctor, CareContext.OUTPATIENT, 3, "b"));
        var fingerprints = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(receipts, times(2)).claim(eq(key), eq(doctor.accountId()), fingerprints.capture());
        assertThat(fingerprints.getAllValues().get(0)).isNotEqualTo(fingerprints.getAllValues().get(1));
    }
    @Test void create_admissionWithoutAuthoritativeEligibility_failsBeforeReceiptOrStock() {
        assertThatThrownBy(() -> service.createCare(key, command(doctor, CareContext.ADMISSION, 2, "a")))
                .hasMessageContaining("Admission creation awaits");
        verifyNoInteractions(receipts, drugs, prescriptions, capture);
    }
    @Test void create_nonDoctorWithStaffId_doesNotReplayReceipt() {
        assertThatThrownBy(() -> service.createCare(key, command(new ActorIdentity(doctor.accountId(), doctor.staffId(), "SYSTEM"), CareContext.OUTPATIENT, 2, "a")));
        verifyNoInteractions(receipts, drugs, prescriptions, capture);
    }
    @Test void create_notEnoughAvailableStock_hasNoPrescriptionOrHeldFact() {
        assertThatThrownBy(() -> service.createCare(key, command(doctor, CareContext.OUTPATIENT, 6, "a")))
                .hasMessageContaining("Insufficient");
        verifyNoInteractions(prescriptions, slips, capture);
        verify(reservations, never()).save(any());
        verify(receipts, never()).complete(any(), any());
    }
    @Test void create_drugExpiresDuringLockWait_rechecksFreshBusinessDate() {
        when(drugs.findByIdForUpdate(drugId)).thenAnswer(call -> {
            when(clock.instant()).thenReturn(Instant.parse("2027-01-02T00:00:00Z"));
            return Optional.of(Drug.restore(drugId, "Server name", null, "tablet", new BigDecimal("50.00"),
                    5, LocalDate.of(2027, 1, 1), null, 1, now, now));
        });
        assertThatThrownBy(() -> service.createCare(key, command(doctor, CareContext.OUTPATIENT, 2, "a"))).hasMessageContaining("expiry");
        verifyNoInteractions(prescriptions, slips, capture);
    }
    private CreatePrescriptionCommand command(ActorIdentity actor, CareContext context, int quantity, String correlation) {
        return new CreatePrescriptionCommand(new CreatePrescriptionRequest(null, patient, doctor.staffId(), department,
                LocalDate.of(2026, 10, 2), List.of(new PrescriptionLineRequest(drugId, quantity, "Daily")), 1, context,
                context == CareContext.OUTPATIENT ? CareEpisodeType.OUTPATIENT_VISIT : CareEpisodeType.ADMISSION,
                episode, context == CareContext.ADMISSION ? episode : null, "DRUG"), actor, correlation);
    }
}
