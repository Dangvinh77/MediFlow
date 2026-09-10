package com.mediflow.lab.application.port.out;

import com.mediflow.lab.application.event.LabRequestCreatedEvent;
import com.mediflow.lab.application.event.LabResultCreatedEvent;

/** Outbound event boundary; RabbitMQ adapters remain outside application. */
public interface LabEventPublisherPort {

    void publishRequestCreated(LabRequestCreatedEvent event);

    void publishResultCreated(LabResultCreatedEvent event);
}
