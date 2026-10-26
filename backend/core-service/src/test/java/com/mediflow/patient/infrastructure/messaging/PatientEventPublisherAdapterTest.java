package com.mediflow.patient.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.patient.application.event.PatientCreatedEvent;
import com.mediflow.patient.application.event.PatientUpdatedEvent;
import com.mediflow.patient.infrastructure.config.RabbitConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PatientEventPublisherAdapterTest {
    private final PatientOutboxStore store = mock(PatientOutboxStore.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final PatientEventPublisherAdapter adapter = new PatientEventPublisherAdapter(store, new org.springframework.amqp.support.converter.Jackson2JsonMessageConverter());

    @Test
    void createdEventRetainsTheFlatWireContractAndOriginalIdentity() throws Exception {
        var event = new PatientCreatedEvent(UUID.randomUUID(), Instant.now(), "corr", UUID.randomUUID(),
                "Nguyen Van A", "a@example.com", "0900000000");
        adapter.publishCreated(event);
        var payload = ArgumentCaptor.forClass(String.class);
        verify(store).append(eq(event.eventId()), eq(event.occurredAt()), eq(event.correlationId()),
                eq(RabbitConfig.RK_PATIENT_CREATED), payload.capture());
        var original = new org.springframework.amqp.support.converter.Jackson2JsonMessageConverter()
                .toMessage(event, new org.springframework.amqp.core.MessageProperties()).getBody();
        assertThat(payload.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(original);
        assertThat(mapper.readTree(payload.getValue()).has("payload")).isFalse();
    }

    @Test
    void archiveEventContainsOnlyIdentityAndCommitsThroughOutbox() throws Exception {
        var event = new com.mediflow.patient.application.event.PatientArchivedEvent(
                UUID.randomUUID(), Instant.now(), "corr", UUID.randomUUID());
        adapter.publishArchived(event);
        var payload = ArgumentCaptor.forClass(String.class);
        verify(store).append(eq(event.eventId()), eq(event.occurredAt()), eq(event.correlationId()),
                eq(RabbitConfig.RK_PATIENT_ARCHIVED), payload.capture());
        var json = mapper.readTree(payload.getValue());
        assertThat(json.size()).isEqualTo(4);
        assertThat(json.get("patientId").asText()).isEqualTo(event.patientId().toString());
    }

    @Test
    void updatedEventIsStoredAndPersistenceFailurePropagatesToRollbackCaller() {
        var event = new PatientUpdatedEvent(UUID.randomUUID(), Instant.now(), "corr", UUID.randomUUID(),
                "Nguyen Van A", "a@example.com", "0900000000", "New address");
        doThrow(new IllegalStateException("storage unavailable")).when(store)
                .append(eq(event.eventId()), eq(event.occurredAt()), eq(event.correlationId()),
                        eq(RabbitConfig.RK_PATIENT_UPDATED), anyString());
        assertThatThrownBy(() -> adapter.publishUpdated(event)).isInstanceOf(IllegalStateException.class);
    }
}
