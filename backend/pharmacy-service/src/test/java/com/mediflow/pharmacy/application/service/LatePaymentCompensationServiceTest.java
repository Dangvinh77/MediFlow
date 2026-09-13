package com.mediflow.pharmacy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.pharmacy.application.dto.command.PaymentCompletedCommand;
import com.mediflow.pharmacy.application.event.PrescriptionDispenseFailedEvent;
import com.mediflow.pharmacy.application.port.out.PharmacyEventPublisherPort;
import com.mediflow.pharmacy.application.port.out.ProcessedEventPort;
import com.mediflow.pharmacy.domain.model.Prescription;
import com.mediflow.pharmacy.domain.model.PrescriptionLine;
import com.mediflow.pharmacy.domain.model.enums.PrescriptionStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Tests the atomic idempotency gate for late-payment compensation. */
class LatePaymentCompensationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-13T09:00:00Z");

    private final ProcessedEventPort processedEventPort = mock(ProcessedEventPort.class);
    private final PharmacyEventPublisherPort eventPublisher = mock(PharmacyEventPublisherPort.class);
    private final LatePaymentCompensationService service = new LatePaymentCompensationService(
            processedEventPort, eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));

    /** A caller that wins the claim emits exactly one traceable compensation event. */
    @Test
    void compensate_unclaimedPayment_claimsAndPublishes() {
        PaymentCompletedCommand command = command();
        Prescription prescription = terminalPrescription(command);
        when(processedEventPort.claimIfAbsent(command.eventId(), "payment.completed")).thenReturn(true);

        boolean compensated = service.compensate(command, prescription);

        assertThat(compensated).isTrue();
        ArgumentCaptor<PrescriptionDispenseFailedEvent> captor =
                ArgumentCaptor.forClass(PrescriptionDispenseFailedEvent.class);
        verify(eventPublisher).publishPrescriptionDispenseFailed(captor.capture());
        assertThat(captor.getValue().occurredAt()).isEqualTo(NOW);
        assertThat(captor.getValue().invoiceId()).isEqualTo(command.invoiceId());
        assertThat(captor.getValue().correlationId()).isEqualTo(command.correlationId());
    }

    /** A concurrent redelivery that loses the claim must not emit duplicate compensation. */
    @Test
    void compensate_alreadyClaimed_doesNotPublish() {
        PaymentCompletedCommand command = command();
        when(processedEventPort.claimIfAbsent(command.eventId(), "payment.completed")).thenReturn(false);

        boolean compensated = service.compensate(command, terminalPrescription(command));

        assertThat(compensated).isFalse();
        verify(eventPublisher, never()).publishPrescriptionDispenseFailed(any());
    }

    private PaymentCompletedCommand command() {
        return new PaymentCompletedCommand(
                UUID.randomUUID(), NOW, "late-payment-correlation", UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("1000.00"), "CASH");
    }

    private Prescription terminalPrescription(PaymentCompletedCommand command) {
        PrescriptionLine line = PrescriptionLine.create(
                UUID.randomUUID(), 1, new BigDecimal("1000.00"), "Ngày 1 lần");
        return Prescription.restore(
                command.prescriptionId(), UUID.randomUUID(), command.patientId(), UUID.randomUUID(),
                command.departmentId(), LocalDate.of(2026, 9, 13), new BigDecimal("1000.00"),
                List.of(line), PrescriptionStatus.CANCELLED, NOW, UUID.randomUUID(), "Đã hủy",
                NOW.minusSeconds(60), NOW);
    }
}
