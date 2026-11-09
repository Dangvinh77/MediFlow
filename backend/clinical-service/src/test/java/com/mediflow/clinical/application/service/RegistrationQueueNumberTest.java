package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.mediflow.clinical.application.dto.request.PaymentResolutionCommand;
import com.mediflow.clinical.application.dto.response.OutpatientRegistrationDTO;
import com.mediflow.clinical.domain.model.OutpatientPaymentOutcome;
import org.junit.jupiter.api.Test;

class RegistrationQueueNumberTest {
    private final IntakeHarness h = new IntakeHarness();

    private OutpatientRegistrationDTO completePayment(boolean flag) {
        var session = h.withCounts(h.session(), 0, 1, 0, 0);
        UUID operation = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        var registration = h.stub(h.paymentLocked(session, operation));
        h.stubAppointment(registration, session);
        when(h.registrations.findPaymentOperationForUpdate(operation))
                .thenReturn(Optional.of(h.lockedReceipt(registration, operation, request)));
        when(h.registrations.allocateQueueNumber(session.sessionId())).thenReturn(4);
        when(h.registrations.allocateDepartmentQueueNumber(h.departmentId, IntakeHarness.TODAY)).thenReturn(9);
        return h.registrationService(flag).resolvePayment(new PaymentResolutionCommand(operation, request,
                registration.registrationId(), registration.reservation().reservationId(), IntakeHarness.FENCE,
                OutpatientPaymentOutcome.COMPLETED, UUID.randomUUID(), UUID.randomUUID(), IntakeHarness.NOW,
                "PATIENT_PAYMENT", null));
    }

    @Test void flagOnUsesDepartmentSequence() {
        assertThat(completePayment(true).queueNumber()).isEqualTo(9);
        verify(h.registrations, never()).allocateQueueNumber(any());
    }

    @Test void flagOffKeepsSessionSequence() {
        assertThat(completePayment(false).queueNumber()).isEqualTo(4);
        verify(h.registrations, never()).allocateDepartmentQueueNumber(any(), any());
    }

    @Test void confirmedFactStillCarriesPositiveQueueNumber() {
        var dto = completePayment(true);
        assertThat(dto.status().name()).isEqualTo("CONFIRMED");
        assertThat(dto.queueNumber()).isPositive();
        verify(h.outbox).appendRawOutpatientEvent(any());
    }
}
