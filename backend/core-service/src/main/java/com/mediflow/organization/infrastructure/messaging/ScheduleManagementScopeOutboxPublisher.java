package com.mediflow.organization.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.organization.application.event.ScheduleManagementScopeChangedEvent;
import com.mediflow.organization.application.port.out.ScheduleManagementScopeEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

/** Stores immutable scope-change event bytes in the same Organization transaction as the scope. */
@Component
@Transactional(transactionManager = "organizationTransactionManager", propagation = Propagation.MANDATORY)
public class ScheduleManagementScopeOutboxPublisher implements ScheduleManagementScopeEventPublisher {
    private final OrganizationEventOutboxStore store;
    private final ObjectMapper objectMapper;

    public ScheduleManagementScopeOutboxPublisher(OrganizationEventOutboxStore store,
            ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(ScheduleManagementScopeChangedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            if (payload.getBytes(StandardCharsets.UTF_8).length == 0) {
                throw new IllegalStateException("Schedule scope event serialized to empty body");
            }
            store.append(event.eventId(), event.occurredAt(), event.correlationId(),
                    ScheduleManagementScopeChangedEvent.ROUTING_KEY,
                    ScheduleManagementScopeChangedEvent.class.getName(), payload);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Schedule scope event could not be serialized", failure);
        }
    }
}
