package com.mediflow.organization.application.port.out;

public interface EventPublisher {

        void publish(Object event);
}
