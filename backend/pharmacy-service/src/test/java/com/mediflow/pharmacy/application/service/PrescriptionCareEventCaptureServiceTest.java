package com.mediflow.pharmacy.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import com.mediflow.pharmacy.application.event.carefinance.PrescriptionCareEvent.EventType;
import com.mediflow.pharmacy.application.port.out.*;
import com.mediflow.pharmacy.domain.model.*;
import com.mediflow.pharmacy.domain.model.enums.*;

class PrescriptionCareEventCaptureServiceTest {
    private final PrescriptionRepositoryPort prescriptions = mock(PrescriptionRepositoryPort.class);
    private final DispenseSlipRepositoryPort slips = mock(DispenseSlipRepositoryPort.class);
    private final PrescriptionCareEventWriterPort writer = mock(PrescriptionCareEventWriterPort.class);
    private final PrescriptionCareEventCaptureService service = new PrescriptionCareEventCaptureService(prescriptions, slips, writer);
    private final UUID id = UUID.randomUUID();
    private final Instant time = Instant.parse("2026-10-01T08:00:00Z");

    @Test
    void creationLocksPrescriptionAndWritesWithoutDispensingOrResolvingCatalogue() {
        when(prescriptions.findByIdForUpdate(id)).thenReturn(Optional.of(rx(PrescriptionStatus.ACTIVE)));
        service.capture(id, UUID.randomUUID(), EventType.CREATED, "trace");
        var order = inOrder(prescriptions, writer);
        order.verify(prescriptions).findByIdForUpdate(id);
        order.verify(writer).storeHeld(any());
        verifyNoInteractions(slips);
        verify(prescriptions, never()).save(any());
    }

    @Test
    void fillRequiresPersistedSlipAndExactBusinessTime() {
        when(prescriptions.findByIdForUpdate(id)).thenReturn(Optional.of(rx(PrescriptionStatus.FULFILLED)));
        when(slips.findByPrescriptionForUpdate(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.capture(id, UUID.randomUUID(), EventType.FILLED, "trace")).hasMessageContaining("slip");
        var slip = DispenseSlip.restore(UUID.randomUUID(), id, DispenseStatus.DISPENSED, time.plusNanos(1), UUID.randomUUID(),
                DispenseActorType.STAFF, null, time, time, time.plusNanos(1));
        when(slips.findByPrescriptionForUpdate(id)).thenReturn(Optional.of(slip));
        assertThatThrownBy(() -> service.capture(id, UUID.randomUUID(), EventType.FILLED, "trace")).hasMessageContaining("timestamp");
        verifyNoInteractions(writer);
    }

    @Test
    void fillDerivesIdentityFromLockedSlipNotFromCaller() {
        when(prescriptions.findByIdForUpdate(id)).thenReturn(Optional.of(rx(PrescriptionStatus.FULFILLED)));
        UUID dispenseId = UUID.randomUUID();
        when(slips.findByPrescriptionForUpdate(id)).thenReturn(Optional.of(DispenseSlip.restore(dispenseId, id,
                DispenseStatus.DISPENSED, time, UUID.randomUUID(), DispenseActorType.STAFF, null, time, time, time)));
        service.capture(id, UUID.randomUUID(), EventType.FILLED, "trace");
        verify(writer).storeHeld(argThat(event -> event.payload().dispenseId().equals(dispenseId)));
    }

    @Test
    void badIdentityIsRejectedBeforeReadingOrWriting() {
        assertThatThrownBy(() -> service.capture(null, UUID.randomUUID(), EventType.CREATED, "trace")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(prescriptions, slips, writer);
    }

    private Prescription rx(PrescriptionStatus status) {
        var care = PrescriptionCareContext.v1(CareContext.OUTPATIENT,
                new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID()), null, "RX");
        var line = PrescriptionLine.restore(UUID.randomUUID(), UUID.randomUUID(), 1, BigDecimal.ONE, null, BigDecimal.ONE, "Snapshot name");
        return Prescription.restore(id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 10, 1), BigDecimal.ONE, List.of(line), status, null, null, null, time, time, care,
                status == PrescriptionStatus.ACTIVE ? null : time);
    }
}
