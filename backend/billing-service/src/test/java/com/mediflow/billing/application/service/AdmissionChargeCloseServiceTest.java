package com.mediflow.billing.application.service;

import com.mediflow.billing.application.dto.command.CloseAdmissionChargesCommand;
import com.mediflow.billing.application.port.out.AdmissionChargeCloseStorePort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AdmissionChargeCloseServiceTest {
    private final AdmissionChargeCloseStorePort store = mock(AdmissionChargeCloseStorePort.class);
    private final Instant now = Instant.parse("2026-10-10T03:00:00Z");
    private final AdmissionChargeCloseService service = new AdmissionChargeCloseService(store, Clock.fixed(now, ZoneOffset.UTC));

    private CloseAdmissionChargesCommand proof(Instant approved) {
        return new CloseAdmissionChargesCommand(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), approved, approved.toString(), "medical-close", "a".repeat(64), "b".repeat(64));
    }

    @Test void exactMedicalTimeDelegatesOnlyChargeClosure() {
        var command = proof(now); service.close(command); verify(store).acceptAndClose(command);
    }

    @Test void futureApprovalFailsBeforeAnyFinancialEffects() {
        assertThatThrownBy(() -> service.close(proof(now.plusNanos(1)))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(store);
    }
}
