package com.mediflow.organization.application.port.out;

/**
 * Output Port để Application publish event.
 *
 * Application không phụ thuộc trực tiếp vào Kafka/RabbitMQ.
 */
public interface EventPublisher {

    /**
     * Publish event.
     */
    void publish(Object event);
}