package com.mediflow.pharmacy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import com.mediflow.pharmacy.application.dto.command.*;
import com.mediflow.pharmacy.application.port.out.*;
import com.mediflow.pharmacy.domain.model.*;
import com.mediflow.pharmacy.domain.model.enums.*;

/** Public/legacy writers must never downgrade V1 to V0, even when called directly. */
class LegacyLifecycleFenceTest {
    private final PrescriptionRepositoryPort prescriptions = mock(PrescriptionRepositoryPort.class);
    private final DispenseSlipRepositoryPort slips = mock(DispenseSlipRepositoryPort.class);
    private final StockReservationRepositoryPort reservations = mock(StockReservationRepositoryPort.class);
    private final PharmacyEventPublisherPort publisher = mock(PharmacyEventPublisherPort.class);
    private final Clock clock = Clock.systemUTC();
    private final UUID id = UUID.randomUUID();

    @Test
    void cancelV1_rejectsBeforeReleaseSlipOrV0Event() {
        when(prescriptions.findByIdForUpdate(id)).thenReturn(Optional.of(rx(PrescriptionStatus.ACTIVE)));
        var service = new CancelPrescriptionService(prescriptions, slips, reservations, publisher, clock);
        assertThatThrownBy(() -> service.cancel(new CancelPrescriptionCommand(id,
                new ActorIdentity(UUID.randomUUID(), null, "ADMIN"), "Cancel", "trace"))).hasMessageContaining("V1 cancellation");
        verifyNoInteractions(slips, reservations, publisher);
    }

    @Test
    void expiryV1_isSkippedWithoutMutationOrLegacyEvent() {
        when(prescriptions.findByIdForUpdate(id)).thenReturn(Optional.of(rx(PrescriptionStatus.ACTIVE)));
        assertThat(new ExpirePrescriptionTransaction(prescriptions, slips, reservations, publisher).expire(id, Instant.now())).isZero();
        verifyNoInteractions(slips, reservations, publisher);
    }

    @Test
    void directFailureV1_rejectsBeforeStockLookupReleaseOrInvoiceCompensation() {
        var drugs = mock(DrugRepositoryPort.class);
        when(prescriptions.findByIdForUpdate(id)).thenReturn(Optional.of(rx(PrescriptionStatus.ACTIVE)));
        var service = new RecordDispenseFailureService(prescriptions, slips, reservations, drugs, publisher, clock);
        assertThatThrownBy(() -> service.record(id, UUID.randomUUID(), UUID.randomUUID(), "trace", "failed"))
                .hasMessageContaining("legacy invoice compensation");
        verifyNoInteractions(slips, reservations, drugs, publisher);
    }

    @Test
    void latePaymentV1_rejectsBeforeDeliveryClaimOrCompensation() {
        var ledger = mock(ProcessedEventPort.class);
        var command = new PaymentCompletedCommand(UUID.randomUUID(), Instant.now(), "trace", UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), id, BigDecimal.ONE, "CASH");
        assertThatThrownBy(() -> new LatePaymentCompensationService(ledger, publisher, clock).compensate(command, rx(PrescriptionStatus.EXPIRED)))
                .hasMessageContaining("legacy payment compensation");
        verifyNoInteractions(ledger, publisher);
    }

    private Prescription rx(PrescriptionStatus status) {
        var care = PrescriptionCareContext.v1(CareContext.OUTPATIENT,
                new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID()), null, "RX");
        return Prescription.restore(id, null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), BigDecimal.ONE,
                List.of(PrescriptionLine.create(UUID.randomUUID(), 1, BigDecimal.ONE, null, "Snapshot name")),
                status, null, null, null, Instant.now(), Instant.now(), care);
    }
}
