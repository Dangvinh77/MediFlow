package com.mediflow.organization.infrastructure.messaging;

import com.mediflow.organization.application.event.StaffCreatedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;

class OrganizationEventOutboxAdapterTest {
    @Test
    void publisherPersistsEventIntentWithStableWireTypeAndBytes() {
        OrganizationEventOutboxStore store = mock(OrganizationEventOutboxStore.class);
        OrganizationEventOutboxAdapter publisher = new OrganizationEventOutboxAdapter(
                store, new Jackson2JsonMessageConverter());
        var event = new StaffCreatedEvent(UUID.randomUUID(), Instant.now(), "trace-1",
                UUID.randomUUID(), "Alex Morgan", UUID.randomUUID(), "NURSE");

        publisher.publishStaffCreated(event);

        verify(store).append(eq(event.eventId()), eq(event.occurredAt()), eq(event.correlationId()),
                eq(StaffCreatedEvent.ROUTING_KEY), eq(StaffCreatedEvent.class.getName()),
                argThat(payload -> payload.contains(event.staffId().toString())
                        && payload.contains(event.fullName())));
    }
}
