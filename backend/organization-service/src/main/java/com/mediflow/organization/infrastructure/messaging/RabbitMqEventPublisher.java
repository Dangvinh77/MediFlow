package com.mediflow.organization.infrastructure.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.mediflow.organization.application.port.out.EventPublisher;
import com.mediflow.organization.application.service.ChangeStaffDepartmentService.StaffDepartmentChangedEvent;
import com.mediflow.organization.application.service.CreateDepartmentService.DepartmentCreatedEvent;
import com.mediflow.organization.application.service.CreateStaffService.StaffCreatedEvent;

@Component
public class RabbitMqEventPublisher implements EventPublisher {

    private static final String EXCHANGE = "mediflow.events";

    private final RabbitTemplate rabbitTemplate;

    public RabbitMqEventPublisher(
            RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(Object event) {

        String routingKey = resolveRoutingKey(event);

        rabbitTemplate.convertAndSend(
                EXCHANGE,
                routingKey,
                event
        );
    }

    private String resolveRoutingKey(Object event) {

        if (event instanceof DepartmentCreatedEvent) {
            return "department.created";
        }

        if (event instanceof StaffCreatedEvent) {
            return "staff.created";
        }

        if (event instanceof StaffDepartmentChangedEvent) {
            return "staff.department.changed";
        }

        throw new IllegalArgumentException(
                "Unsupported event type: "
                        + event.getClass().getName()
        );
    }
}