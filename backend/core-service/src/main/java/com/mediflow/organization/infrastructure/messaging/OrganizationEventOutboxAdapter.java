package com.mediflow.organization.infrastructure.messaging;

import com.mediflow.organization.application.event.DepartmentCreatedEvent;
import com.mediflow.organization.application.event.StaffCreatedEvent;
import com.mediflow.organization.application.event.StaffDepartmentChangedEvent;
import com.mediflow.organization.application.port.out.EventPublisher;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

/** Persists immutable integration-event bytes in the same Org transaction as the aggregate. */
@Component
@Transactional(transactionManager = "organizationTransactionManager", propagation = Propagation.MANDATORY)
public class OrganizationEventOutboxAdapter implements EventPublisher {
    private final OrganizationEventOutboxStore store;
    private final MessageConverter converter;

    public OrganizationEventOutboxAdapter(OrganizationEventOutboxStore store, MessageConverter converter) {
        this.store = store;
        this.converter = converter;
    }

    @Override
    public void publishDepartmentCreated(DepartmentCreatedEvent event) {
        append(event, DepartmentCreatedEvent.ROUTING_KEY, DepartmentCreatedEvent.class.getName());
    }

    @Override
    public void publishStaffCreated(StaffCreatedEvent event) {
        append(event, StaffCreatedEvent.ROUTING_KEY, StaffCreatedEvent.class.getName());
    }

    @Override
    public void publishStaffDepartmentChanged(StaffDepartmentChangedEvent event) {
        append(event, StaffDepartmentChangedEvent.ROUTING_KEY, StaffDepartmentChangedEvent.class.getName());
    }

    private void append(Object event, String routingKey, String typeId) {
        MessageProperties properties = new MessageProperties();
        byte[] body = converter.toMessage(event, properties).getBody();
        if (body.length == 0) throw new IllegalStateException("Organization event serialized to empty body");
        var common = switch (event) {
            case DepartmentCreatedEvent value -> new EventMetadata(value.eventId(), value.occurredAt(), value.correlationId());
            case StaffCreatedEvent value -> new EventMetadata(value.eventId(), value.occurredAt(), value.correlationId());
            case StaffDepartmentChangedEvent value -> new EventMetadata(value.eventId(), value.occurredAt(), value.correlationId());
            default -> throw new IllegalArgumentException("Unsupported organization event type");
        };
        store.append(common.id(), common.occurredAt(), common.correlationId(), routingKey,
                typeId, new String(body, StandardCharsets.UTF_8));
    }

    private record EventMetadata(java.util.UUID id, java.time.Instant occurredAt, String correlationId) {}
}
