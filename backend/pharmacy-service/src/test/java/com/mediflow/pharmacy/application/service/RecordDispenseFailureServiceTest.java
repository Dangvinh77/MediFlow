package com.mediflow.pharmacy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.pharmacy.application.event.PrescriptionDispenseFailedEvent;
import com.mediflow.pharmacy.application.port.out.DispenseSlipRepositoryPort;
import com.mediflow.pharmacy.application.port.out.DrugRepositoryPort;
import com.mediflow.pharmacy.application.port.out.PharmacyEventPublisherPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;
import com.mediflow.pharmacy.application.port.out.StockReservationRepositoryPort;
import com.mediflow.pharmacy.domain.model.DispenseSlip;
import com.mediflow.pharmacy.domain.model.Drug;
import com.mediflow.pharmacy.domain.model.Prescription;
import com.mediflow.pharmacy.domain.model.PrescriptionLine;
import com.mediflow.pharmacy.domain.model.StockReservation;
import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;
import com.mediflow.pharmacy.domain.model.enums.PrescriptionStatus;
import com.mediflow.pharmacy.domain.model.enums.ReservationReleaseReason;
import com.mediflow.pharmacy.domain.model.enums.ReservationStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Tests the post-rollback transaction that persists BR-D6 and BR-D12. */
class RecordDispenseFailureServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-13T09:00:00Z");

    /** Failure recording releases reservations and emits bounded item-level detail. */
    @Test
    void record_activeAggregate_marksFailedAndPublishesItems() {
        PrescriptionRepositoryPort prescriptionRepo = mock(PrescriptionRepositoryPort.class);
        DispenseSlipRepositoryPort slipRepo = mock(DispenseSlipRepositoryPort.class);
        StockReservationRepositoryPort reservationRepo = mock(StockReservationRepositoryPort.class);
        DrugRepositoryPort drugRepo = mock(DrugRepositoryPort.class);
        PharmacyEventPublisherPort publisher = mock(PharmacyEventPublisherPort.class);
        RecordDispenseFailureService service = new RecordDispenseFailureService(
                prescriptionRepo, slipRepo, reservationRepo, drugRepo, publisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
        UUID prescriptionId = UUID.randomUUID();
        UUID drugId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        PrescriptionLine line = PrescriptionLine.create(
                drugId, 2, new BigDecimal("500.00"), "Ngày 2 lần");
        Prescription prescription = Prescription.restore(
                prescriptionId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 9, 13), new BigDecimal("1000.00"), List.of(line),
                PrescriptionStatus.ACTIVE, null, null, null, NOW.minusSeconds(60), NOW.minusSeconds(60));
        DispenseSlip slip = DispenseSlip.restore(
                UUID.randomUUID(), prescriptionId, DispenseStatus.PENDING,
                null, null, null, NOW.minusSeconds(60), NOW.minusSeconds(60));
        StockReservation reservation = StockReservation.restore(
                UUID.randomUUID(), drugId, prescriptionId, 2, ReservationStatus.RESERVED,
                NOW.minusSeconds(60), NOW.plusSeconds(60), NOW.minusSeconds(60), null, null, null);
        Drug drug = Drug.restore(
                drugId, "Paracetamol", "Paracetamol", "viên", new BigDecimal("500.00"),
                5, LocalDate.of(2027, 1, 1), "MediFlow", 1, NOW, NOW);
        when(prescriptionRepo.findByIdForUpdate(prescriptionId)).thenReturn(Optional.of(prescription));
        when(slipRepo.findByPrescriptionForUpdate(prescriptionId)).thenReturn(Optional.of(slip));
        when(reservationRepo.findByPrescriptionForUpdate(prescriptionId)).thenReturn(List.of(reservation));
        when(drugRepo.findById(drugId)).thenReturn(Optional.of(drug));
        when(reservationRepo.findReservedByDrug(drugId)).thenReturn(List.of());

        service.record(
                prescriptionId, actorId, invoiceId, "failure-correlation",
                "RESERVATION_MISSING: " + "x".repeat(600));

        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.DISPENSE_FAILED);
        assertThat(slip.getStatus()).isEqualTo(DispenseStatus.FAILED);
        assertThat(slip.getFailureReason()).hasSize(500);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(reservation.getReleaseReason()).isEqualTo(ReservationReleaseReason.DISPENSE_FAILED);
        ArgumentCaptor<PrescriptionDispenseFailedEvent> captor =
                ArgumentCaptor.forClass(PrescriptionDispenseFailedEvent.class);
        verify(publisher).publishPrescriptionDispenseFailed(captor.capture());
        assertThat(captor.getValue().invoiceId()).isEqualTo(invoiceId);
        assertThat(captor.getValue().failedItems()).singleElement().satisfies(item -> {
            assertThat(item.drugId()).isEqualTo(drugId);
            assertThat(item.requestedQty()).isEqualTo(2);
            assertThat(item.availableQty()).isEqualTo(5);
        });
        verify(prescriptionRepo).save(any(Prescription.class));
    }
}
