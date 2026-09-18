package com.mediflow.pharmacy.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.application.event.PrescriptionCreatedEvent;
import com.mediflow.pharmacy.application.event.PrescriptionFilledEvent;
import com.mediflow.pharmacy.infrastructure.persistence.jpaentity.PharmacyEventOutboxJpaEntity;
import com.mediflow.pharmacy.infrastructure.persistence.repository.PharmacyEventOutboxJpaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Tests that application events cross the adapter only through the transactional outbox. */
class PharmacyEventPublisherAdapterTest {

    private final PharmacyEventOutboxJpaRepository repository =
            mock(PharmacyEventOutboxJpaRepository.class);
    private final ObjectMapper objectMapper = mock(ObjectMapper.class);
    private final PharmacyEventPublisherAdapter adapter =
            new PharmacyEventPublisherAdapter(repository, objectMapper);

    /** A created event is serialized under the exact contract routing key. */
    @Test
    void publishPrescriptionCreated_validEvent_persistsSerializedOutboxRow() throws Exception {
        PrescriptionCreatedEvent event = event();
        when(objectMapper.writeValueAsString(event)).thenReturn("{\"eventId\":\"test\"}");

        adapter.publishPrescriptionCreated(event);

        ArgumentCaptor<PharmacyEventOutboxJpaEntity> captor =
                ArgumentCaptor.forClass(PharmacyEventOutboxJpaEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo(event.eventId());
        assertThat(captor.getValue().getRoutingKey()).isEqualTo("prescription.created");
        assertThat(captor.getValue().getAggregateId()).isEqualTo(event.prescriptionId());
        assertThat(captor.getValue().getPayload()).isEqualTo("{\"eventId\":\"test\"}");
    }

    /** Serialization failure aborts the business transaction instead of storing corrupt JSON. */
    @Test
    void publishPrescriptionCreated_serializationFails_doesNotPersist() throws Exception {
        PrescriptionCreatedEvent event = event();
        when(objectMapper.writeValueAsString(event)).thenThrow(new JsonProcessingException("bad event") { });

        assertThatThrownBy(() -> adapter.publishPrescriptionCreated(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("serialize");

        verify(repository, never()).save(any());
    }

    /** The filled payload keeps the producer-owned record correlation in the durable outbox. */
    @Test
    void publishPrescriptionFilled_serializesRecordIdInOutboxPayload() throws Exception {
        ObjectMapper realObjectMapper = new ObjectMapper().findAndRegisterModules();
        PharmacyEventPublisherAdapter realAdapter =
                new PharmacyEventPublisherAdapter(repository, realObjectMapper);
        UUID recordId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        PrescriptionFilledEvent event = new PrescriptionFilledEvent(
                UUID.randomUUID(),
                Instant.parse("2026-09-13T09:00:00Z"),
                "correlation-filled",
                UUID.randomUUID(),
                recordId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("2000.00"),
                List.of(new PrescriptionFilledEvent.DispensedItem(
                        UUID.randomUUID(), "Paracetamol", 2)));

        realAdapter.publishPrescriptionFilled(event);

        ArgumentCaptor<PharmacyEventOutboxJpaEntity> captor =
                ArgumentCaptor.forClass(PharmacyEventOutboxJpaEntity.class);
        verify(repository).save(captor.capture());
        PharmacyEventOutboxJpaEntity outbox = captor.getValue();
        assertThat(outbox.getRoutingKey()).isEqualTo("prescription.filled");
        assertThat(outbox.getAggregateId()).isEqualTo(event.prescriptionId());
        assertThat(realObjectMapper.readTree(outbox.getPayload()).get("recordId").asText())
                .isEqualTo(recordId.toString());
    }

    private PrescriptionCreatedEvent event() {
        return new PrescriptionCreatedEvent(
                UUID.randomUUID(),
                Instant.parse("2026-09-13T09:00:00Z"),
                "correlation-test",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("2000.00"),
                List.of(new PrescriptionCreatedEvent.Item(
                        UUID.randomUUID(), "Paracetamol", 2, new BigDecimal("1000.00"))));
    }
}
