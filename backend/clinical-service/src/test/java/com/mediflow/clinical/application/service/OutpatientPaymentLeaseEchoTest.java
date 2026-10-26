package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.dto.request.PaymentLockCommand;
import com.mediflow.clinical.application.port.out.AppointmentRepositoryPort;
import com.mediflow.clinical.application.port.out.ClinicalOutboxPort;
import com.mediflow.clinical.application.port.out.ConsultationSessionRepositoryPort;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.FollowUpRecommendationRepositoryPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.OutpatientRegistrationRepositoryPort;
import com.mediflow.clinical.application.port.out.PatientLookupPort;
import com.mediflow.clinical.application.port.out.SchedulingAuthorityPort;
import com.mediflow.clinical.domain.model.OutpatientPaymentOperationState;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutpatientPaymentLeaseEchoTest {
    private static final Instant NOW = Instant.parse("2026-10-26T08:00:00Z");

    @Test
    void acquireReplayReturnsThePersistedEligibilityBasisAndQuoteId() throws Exception {
        var registrations = mock(OutpatientRegistrationRepositoryPort.class);
        UUID operationId = UUID.randomUUID();
        UUID paymentRequestId = UUID.randomUUID();
        UUID registrationId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();
        var receipt = new OutpatientRegistrationRepositoryPort.PaymentOperationReceipt(operationId,
                paymentRequestId, registrationId, reservationId, patientId, "EXAM", registrationId,
                registrationId, 4, 5, 12L, OutpatientPaymentOperationState.PAYMENT_LOCKED,
                NOW, NOW.plusSeconds(120), null, null, null, null, "ZERO_LIABILITY", quoteId);
        when(registrations.findPaymentOperationForUpdate(operationId)).thenReturn(Optional.of(receipt));
        var service = new OutpatientRegistrationApplicationService(
                mock(ConsultationSessionRepositoryPort.class), registrations,
                mock(AppointmentRepositoryPort.class), mock(PatientLookupPort.class),
                mock(SchedulingAuthorityPort.class), mock(CurrentClinicalActorPort.class),
                mock(ClinicalOutboxPort.class), mock(CorrelationIdProvider.class),
                mock(IdempotentCommandPort.class), Clock.fixed(NOW, ZoneOffset.UTC),
                mock(MedicalRecordRepositoryPort.class), mock(FollowUpRecommendationRepositoryPort.class));
        var command = new PaymentLockCommand(operationId, paymentRequestId, registrationId, patientId,
                "EXAM", registrationId, registrationId, 4, "ZERO_LIABILITY", quoteId);

        var lease = service.acquirePayment(reservationId, command);
        var json = new ObjectMapper().findAndRegisterModules().readTree(
                new ObjectMapper().findAndRegisterModules().writeValueAsBytes(lease));

        assertThat(json.path("basis").asText()).isEqualTo("ZERO_LIABILITY");
        assertThat(json.path("quoteId").asText()).isEqualTo(quoteId.toString());
        verify(registrations).lockPaymentOperation(operationId);
    }
}
