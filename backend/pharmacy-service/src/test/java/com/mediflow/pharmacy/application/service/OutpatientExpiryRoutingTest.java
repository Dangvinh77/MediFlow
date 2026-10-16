package com.mediflow.pharmacy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.mediflow.pharmacy.application.port.in.OutpatientPrescriptionLifecycleUseCase;
import com.mediflow.pharmacy.application.port.out.*;
import com.mediflow.pharmacy.domain.model.*;
import com.mediflow.pharmacy.domain.model.enums.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutpatientExpiryRoutingTest {
    private final PrescriptionRepositoryPort prescriptions = mock(PrescriptionRepositoryPort.class);
    private final DispenseSlipRepositoryPort slips = mock(DispenseSlipRepositoryPort.class);
    private final StockReservationRepositoryPort reservations = mock(StockReservationRepositoryPort.class);
    private final PharmacyEventPublisherPort legacy = mock(PharmacyEventPublisherPort.class);
    private final OutpatientPrescriptionLifecycleUseCase lifecycle = mock(OutpatientPrescriptionLifecycleUseCase.class);
    private final UUID id = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-14T01:00:00Z");

    @Test
    void enabledOutpatientUsesV1PortAndNotLegacyExpiryEvent() {
        stub(PrescriptionCareContext.v1(CareContext.OUTPATIENT,
                new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID()), null, "RX"));
        when(lifecycle.expire(id, "outpatient-expiry-" + id)).thenReturn(1);
        assertThat(wrapper(Optional.of(lifecycle)).expire(id, now.minusSeconds(100))).isOne();
        verify(lifecycle).expire(id, "outpatient-expiry-" + id);
        verifyNoInteractions(slips, reservations, legacy);
    }

    @Test
    void disabledPipelineLeavesV1HeldWithoutLegacyFallback() {
        stub(PrescriptionCareContext.v1(CareContext.OUTPATIENT,
                new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID()), null, "RX"));
        assertThat(wrapper(Optional.empty()).expire(id, now)).isZero();
        verifyNoInteractions(lifecycle, slips, reservations, legacy);
    }

    @Test
    void evenEnabledPipelineCannotExpireDeferredAdmission() {
        UUID admission = UUID.randomUUID();
        stub(PrescriptionCareContext.v1(CareContext.ADMISSION,
                new CareEpisode(CareEpisodeType.ADMISSION, admission), admission, "RX"));
        assertThat(wrapper(Optional.of(lifecycle)).expire(id, now)).isZero();
        verifyNoInteractions(lifecycle, slips, reservations, legacy);
    }

    private ExpirePrescriptionTransaction wrapper(Optional<OutpatientPrescriptionLifecycleUseCase> enabled) {
        return new ExpirePrescriptionTransaction(prescriptions, slips, reservations, legacy, enabled);
    }

    private void stub(PrescriptionCareContext context) {
        var line = PrescriptionLine.create(UUID.randomUUID(), 1, new BigDecimal("10"), "Daily", "Stored");
        var prescription = Prescription.restore(id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), LocalDate.of(2026, 10, 14), new BigDecimal("10"), List.of(line),
                PrescriptionStatus.ACTIVE, null, null, null, now, now, context);
        when(prescriptions.findByIdForUpdate(id)).thenReturn(Optional.of(prescription));
    }
}
