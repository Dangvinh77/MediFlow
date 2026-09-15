package com.mediflow.pharmacy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mediflow.pharmacy.application.event.PrescriptionExpiredEvent;
import com.mediflow.pharmacy.application.port.out.DispenseSlipRepositoryPort;
import com.mediflow.pharmacy.application.port.out.PharmacyEventPublisherPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;
import com.mediflow.pharmacy.application.port.out.StockReservationRepositoryPort;
import com.mediflow.pharmacy.domain.model.DispenseSlip;
import com.mediflow.pharmacy.domain.model.Prescription;
import com.mediflow.pharmacy.domain.model.PrescriptionLine;
import com.mediflow.pharmacy.domain.model.StockReservation;
import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;
import com.mediflow.pharmacy.domain.model.enums.PrescriptionStatus;
import com.mediflow.pharmacy.domain.model.enums.ReservationStatus;

/** Verifies expiry's quantity integrity and deterministic logical event identity. */
class ExpirePrescriptionTransactionTest {

    /** Expiry does not mutate a legacy reservation whose quantity differs from its line. */
    @Test
    void expire_quantityMismatch_isLeftForReconciliation() {
        UUID id = UUID.randomUUID();
        UUID drugId = UUID.randomUUID();
        Prescription prescription = prescription(id, drugId, 2);
        DispenseSlip slip = slip(id);
        StockReservation reservation = reservation(id, drugId, 9, Instant.parse("2026-09-12T00:00:00Z"));
        PrescriptionRepositoryPort prescriptions = mock(PrescriptionRepositoryPort.class);
        DispenseSlipRepositoryPort slips = mock(DispenseSlipRepositoryPort.class);
        StockReservationRepositoryPort reservations = mock(StockReservationRepositoryPort.class);
        PharmacyEventPublisherPort publisher = mock(PharmacyEventPublisherPort.class);
        when(prescriptions.findByIdForUpdate(id)).thenReturn(Optional.of(prescription));
        when(slips.findByPrescriptionForUpdate(id)).thenReturn(Optional.of(slip));
        when(reservations.findByPrescriptionForUpdate(id)).thenReturn(List.of(reservation));

        assertThat(new ExpirePrescriptionTransaction(prescriptions, slips, reservations, publisher)
                .expire(id, Instant.parse("2026-09-13T00:00:00Z"))).isZero();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RESERVED);
    }

    /** Repeated evaluation of the same aggregate uses the same expiry logical event id. */
    @Test
    void expire_eventId_isDeterministicForLogicalAggregate() {
        UUID id = UUID.randomUUID();
        PrescriptionRepositoryPort prescriptions = mock(PrescriptionRepositoryPort.class);
        DispenseSlipRepositoryPort slips = mock(DispenseSlipRepositoryPort.class);
        StockReservationRepositoryPort reservations = mock(StockReservationRepositoryPort.class);
        PharmacyEventPublisherPort publisher = mock(PharmacyEventPublisherPort.class);
        UUID drugId = UUID.randomUUID();
        when(prescriptions.findByIdForUpdate(id)).thenReturn(Optional.of(prescription(id, drugId, 2)));
        when(slips.findByPrescriptionForUpdate(id)).thenReturn(Optional.of(slip(id)));
        when(reservations.findByPrescriptionForUpdate(id)).thenReturn(
                List.of(reservation(id, drugId, 2, Instant.parse("2026-09-12T00:00:00Z"))));

        new ExpirePrescriptionTransaction(prescriptions, slips, reservations, publisher)
                .expire(id, Instant.parse("2026-09-13T00:00:00Z"));

        ArgumentCaptor<PrescriptionExpiredEvent> event = ArgumentCaptor.forClass(PrescriptionExpiredEvent.class);
        verify(publisher).publishPrescriptionExpired(event.capture());
        assertThat(event.getValue().eventId())
                .isEqualTo(UUID.nameUUIDFromBytes(("prescription.expired:" + id)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private Prescription prescription(UUID id, UUID drugId, int quantity) {
        PrescriptionLine line = PrescriptionLine.create(
                drugId, quantity, new BigDecimal("10.00"), "once");
        return Prescription.restore(id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 9, 12), new BigDecimal("20.00"), List.of(line),
                PrescriptionStatus.ACTIVE, null, null, null, Instant.now(), Instant.now());
    }

    private DispenseSlip slip(UUID id) {
        return DispenseSlip.restore(UUID.randomUUID(), id, DispenseStatus.PENDING,
                null, null, null, Instant.now(), Instant.now());
    }

    private StockReservation reservation(UUID id, UUID drugId, int quantity, Instant expiresAt) {
        return StockReservation.restore(UUID.randomUUID(), drugId, id, quantity,
                ReservationStatus.RESERVED, Instant.now(), expiresAt, Instant.now(),
                null, null, null);
    }
}
