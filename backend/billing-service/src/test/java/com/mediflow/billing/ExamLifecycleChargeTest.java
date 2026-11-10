package com.mediflow.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.dto.command.ExamChargeCommand;
import com.mediflow.billing.application.port.in.AccrueFeeUseCase;
import com.mediflow.billing.application.port.in.IssueExamChargeUseCase;
import com.mediflow.billing.application.port.in.SagaCompensationUseCase;
import com.mediflow.billing.application.port.in.SurgeryChargeUseCase;
import com.mediflow.billing.infrastructure.messaging.ExamChargeDecoder;
import com.mediflow.billing.messaging.consumer.BillingEventConsumer;
import com.mediflow.billing.messaging.consumer.ExamChargeConsumer;
import com.mediflow.billing.messaging.consumer.LabTestChargeConsumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

/** A visit is paid for once: starting, ending or redelivering its facts must not raise a second exam charge. */
class ExamLifecycleChargeTest {
    private static final String STATUS_KEY = "appointment.status.changed";

    private final ObjectMapper mapper = new ObjectMapper();
    private final IssueExamChargeUseCase charges = mock(IssueExamChargeUseCase.class);
    private final ExamChargeConsumer consumer = new ExamChargeConsumer(new ExamChargeDecoder(mapper), charges);

    private static Message message(String key, String json) {
        var properties = new MessageProperties();
        properties.setReceivedRoutingKey(key);
        return new Message(json.getBytes(StandardCharsets.UTF_8), properties);
    }

    private static String statusEvent(UUID eventId, UUID appointment, String recordId, String from, String to) {
        return """
                {"eventId":"%s","eventType":"appointment.status.changed","version":1,"occurredAt":"2026-11-10T01:00:00Z",
                 "correlationId":"00000000-0000-4000-8000-000000000007","producer":"clinical-service",
                 "payload":{"appointmentId":"%s","recordId":%s,"patientId":"00000000-0000-4000-8000-000000000003",
                 "departmentId":"00000000-0000-4000-8000-000000000004","oldStatus":"%s","newStatus":"%s",
                 "careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"%s","sourceType":"EXAM","sourceId":"%s",
                 "priceCode":"OUTPATIENT_EXAM","changedAt":"2026-11-10T01:00:00Z","emergencyOverrideId":null}}
                """.formatted(eventId, appointment, recordId, from, to, appointment, appointment);
    }

    private static String withRecord(UUID record) {
        return "\"" + record + "\"";
    }

    @Test void scheduledLifecycleIssuesNoExamCharge() {
        UUID appointment = UUID.randomUUID();
        String record = withRecord(UUID.randomUUID());
        String started = statusEvent(UUID.randomUUID(), appointment, record, "READY_FOR_EXAM", "IN_EXAM");
        String finished = statusEvent(UUID.randomUUID(), appointment, record, "IN_EXAM", "COMPLETED");
        consumer.receive(message(STATUS_KEY, started));
        consumer.receive(message(STATUS_KEY, finished));
        consumer.receive(message(STATUS_KEY, started));
        consumer.receive(message(STATUS_KEY, finished));
        verify(charges, never()).issue(any());
    }

    @Test void medicalRecordCreatedIsInformationalWithV1Issuer() throws Exception {
        AccrueFeeUseCase accrue = mock(AccrueFeeUseCase.class);
        var billing = new BillingEventConsumer(accrue, mock(SagaCompensationUseCase.class), mock(SurgeryChargeUseCase.class), mapper,
                null, null, null, null, null, mock(LabTestChargeConsumer.class), null, null, mock(ExamChargeConsumer.class), null);
        try (var fixture = getClass().getResourceAsStream("/contracts/outpatient-lifecycle/medicalrecord.created.v1.json")) {
            billing.onMessage(message("medicalrecord.created", new String(fixture.readAllBytes(), StandardCharsets.UTF_8)));
        }
        verifyNoInteractions(accrue);
    }

    @Test void returnVisitIsChargedOnceAsItsOwnSource() {
        UUID earlier = UUID.randomUUID();
        UUID returnVisit = UUID.randomUUID();
        UUID event = UUID.randomUUID();
        String checkIn = statusEvent(event, returnVisit, "null", "PENDING", "AWAITING_PAYMENT");
        consumer.receive(message(STATUS_KEY, statusEvent(UUID.randomUUID(), earlier, "null", "PENDING", "AWAITING_PAYMENT")));
        consumer.receive(message(STATUS_KEY, checkIn));
        consumer.receive(message(STATUS_KEY, checkIn));
        var issued = ArgumentCaptor.forClass(ExamChargeCommand.class);
        verify(charges, times(3)).issue(issued.capture());
        var commands = issued.getAllValues();
        assertThat(commands.get(0).appointmentId()).isEqualTo(earlier);
        assertThat(commands.get(1).appointmentId()).isEqualTo(returnVisit);
        assertThat(commands.get(2)).as("a redelivery names the same source and event, so the issuer can keep it to one charge")
                .isEqualTo(commands.get(1));
    }
}
