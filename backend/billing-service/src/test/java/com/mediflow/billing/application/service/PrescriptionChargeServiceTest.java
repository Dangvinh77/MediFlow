package com.mediflow.billing.application.service;

import com.mediflow.billing.application.dto.command.PrescriptionChargeCommand;
import com.mediflow.billing.application.event.LedgerIntegrationEvent;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.application.port.out.PrescriptionChargeRepositoryPort;
import com.mediflow.billing.application.port.out.PrescriptionPricePolicyPort;
import com.mediflow.billing.application.port.out.PrescriptionChargeRepositoryPort.IssuedCharge;
import com.mediflow.billing.domain.model.Charge;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrescriptionChargeServiceTest {
    private final PrescriptionChargeRepositoryPort repository = mock(PrescriptionChargeRepositoryPort.class);
    private final LedgerEventPort events = mock(LedgerEventPort.class);
    private final Instant now = Instant.parse("2026-10-09T08:00:00Z");
    private final PrescriptionPricePolicyPort prices = mock(PrescriptionPricePolicyPort.class);
    private final PrescriptionChargeService service = new PrescriptionChargeService(repository, events, Clock.fixed(now, ZoneOffset.UTC), prices);

    @Test void issue_outpatient_exactBundleSelectedRequestAndHeldNotice() {
        var c = command("OUTPATIENT"); UUID account = UUID.randomUUID();
        var issued = new IssuedCharge(UUID.randomUUID(), account, UUID.randomUUID(), UUID.randomUUID());
        when(repository.lockRecordedSource(c)).thenReturn(Optional.empty()); when(repository.openAndLockExactAccount(c)).thenReturn(account);
        when(repository.saveChargeAndRequest(eq(c), eq(account), any(), eq(now))).thenReturn(issued);
        assertThat(service.issue(c)).isEqualTo(issued);
        var charge = ArgumentCaptor.forClass(Charge.class); verify(repository).saveChargeAndRequest(eq(c), eq(account), charge.capture(), eq(now));
        assertThat(charge.getValue().getSourceType()).isEqualTo("PRESCRIPTION");
        assertThat(charge.getValue().getSourceId()).isEqualTo(c.prescriptionId());
        assertThat(charge.getValue().getQuantity()).isEqualByComparingTo("1");
        assertThat(charge.getValue().getGrossAmount()).isEqualByComparingTo("2000");
        var event = ArgumentCaptor.forClass(LedgerIntegrationEvent.class); verify(events).appendHeld(eq(account), event.capture());
        var payload = (LedgerIntegrationEvent.PrescriptionPaymentRequestPayload) event.getValue().payload();
        assertThat(payload.prescriptionId()).isEqualTo(c.prescriptionId());
        assertThat(payload.invoiceId()).isEqualTo(issued.invoiceId()).isNotEqualTo(issued.paymentRequestId());
        assertThat(payload.createdAt()).isEqualTo(now);
    }
    @Test void issue_admission_noPrepaymentInvoiceOrClearance() {
        var c = command("ADMISSION"); UUID account = UUID.randomUUID();
        var issued = new IssuedCharge(UUID.randomUUID(), account, null, null);
        when(repository.lockRecordedSource(c)).thenReturn(Optional.empty()); when(repository.openAndLockExactAccount(c)).thenReturn(account);
        when(repository.saveChargeAndRequest(eq(c), eq(account), any(), eq(now))).thenReturn(issued);
        assertThat(service.issue(c)).isEqualTo(issued); verifyNoInteractions(events);
    }
    @Test void issue_replay_noAccountReopenRepricingOrNewEvent() {
        var c = command("OUTPATIENT"); var issued = new IssuedCharge(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(repository.lockRecordedSource(c)).thenReturn(Optional.of(issued));
        assertThat(service.issue(c)).isEqualTo(issued);
        verify(repository).claimDelivery(c); verify(repository).lockRecordedSource(c); verifyNoMoreInteractions(repository); verifyNoInteractions(events, prices);
    }
    @Test void issue_accountDenial_noChargeOrNotice() {
        var c = command("OUTPATIENT"); when(repository.lockRecordedSource(c)).thenReturn(Optional.empty());
        when(repository.openAndLockExactAccount(c)).thenThrow(new IllegalStateException("closed"));
        assertThatThrownBy(() -> service.issue(c)).hasMessage("closed");
        verify(repository, never()).saveChargeAndRequest(any(), any(), any(), any()); verifyNoInteractions(events);
    }
    @Test void issue_unapprovedPriceCode_noAccountChargeOrNotice() {
        var c = command("OUTPATIENT"); when(repository.lockRecordedSource(c)).thenReturn(Optional.empty());
        doThrow(new IllegalArgumentException("unsupported code")).when(prices).requireAllowed(c.priceCode());
        assertThatThrownBy(() -> service.issue(c)).hasMessage("unsupported code");
        verify(repository, never()).openAndLockExactAccount(any()); verifyNoInteractions(events);
    }
    private PrescriptionChargeCommand command(String context) {
        UUID episode = UUID.randomUUID();
        return new PrescriptionChargeCommand(UUID.randomUUID(), "a".repeat(64), "b".repeat(64), "rx-test",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), context,
                "ADMISSION".equals(context) ? "ADMISSION" : "OUTPATIENT_VISIT", episode,
                "ADMISSION".equals(context) ? episode : null, "RX-DEFAULT",
                List.of(new PrescriptionChargeCommand.Item(UUID.randomUUID(), "Synthetic drug", 2, new BigDecimal("1000"))),
                new BigDecimal("2000"), now.minusSeconds(60), "c".repeat(64));
    }
}
