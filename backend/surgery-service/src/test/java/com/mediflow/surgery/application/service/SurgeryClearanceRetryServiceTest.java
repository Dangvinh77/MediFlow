package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryInboxPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SurgeryClearanceRetryServiceTest {
    private final SurgeryInboxPort inbox = mock(SurgeryInboxPort.class);
    private final SurgeryClockPort clock = mock(SurgeryClockPort.class);
    private final SurgeryClearanceRetryService service = new SurgeryClearanceRetryService(inbox, clock);
    private final Instant now = Instant.parse("2026-10-05T08:01:00Z");

    @ParameterizedTest
    @EnumSource(value = SurgeryInboxPort.Decision.class, names = {"NEW", "RETRY_PENDING"})
    void deferFailure_retryableRow_persistsBackoffUsingFreshClock(SurgeryInboxPort.Decision decision) {
        var event = event("financial.clearance.granted", 1, "billing-service");
        when(inbox.begin(event)).thenReturn(decision);
        when(clock.now()).thenReturn(now);
        service.deferFailure(event);
        var order = inOrder(inbox, clock);
        order.verify(inbox).begin(event);
        order.verify(clock).now();
        order.verify(inbox).defer(event.eventId(), "SURGERY_CLEARANCE_RETRY_UNAVAILABLE", now.plusSeconds(60));
        verifyNoMoreInteractions(inbox, clock);
    }

    @ParameterizedTest
    @EnumSource(value = SurgeryInboxPort.Decision.class, names = {"ALREADY_APPLIED", "CONFLICT", "QUARANTINED"})
    void deferFailure_terminalWinner_isNeverReopened(SurgeryInboxPort.Decision decision) {
        var event = event("financial.clearance.granted", 1, "billing-service");
        when(inbox.begin(event)).thenReturn(decision);
        service.deferFailure(event);
        verify(inbox).begin(event);
        verifyNoMoreInteractions(inbox);
        verifyNoInteractions(clock);
    }

    @Test
    void deferFailure_wrongContract_neverClaimsOrDefers() {
        assertThatThrownBy(() -> service.deferFailure(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.deferFailure(event("payment.completed", 1, "billing-service")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.deferFailure(event("financial.clearance.granted", 2, "billing-service")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.deferFailure(event("financial.clearance.granted", 1, "clinical-service")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(inbox, clock);
    }

    private SurgeryInboxPort.IncomingEvent event(String type, int version, String producer) {
        return new SurgeryInboxPort.IncomingEvent(UUID.randomUUID(), type, version, producer,
                "a".repeat(64), "grant:" + UUID.randomUUID(), new byte[]{1, 2}, now);
    }
}
