package com.mediflow.pharmacy.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mediflow.pharmacy.infrastructure.config.RabbitConfig;
import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.PharmacyEventOutboxJpaEntity;
import com.mediflow.pharmacy.infrastructure.persistence.repository.PharmacyEventOutboxJpaRepository;

/**
 * Exercises the outbox dispatcher against a real RabbitMQ broker.
 *
 * <p>Mocking {@link RabbitTemplate} cannot prove publisher confirms or mandatory returns, so this
 * test declares a temporary queue and observes the message delivered by the real broker.</p>
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.publisher-confirm-type=correlated",
        "spring.rabbitmq.template.mandatory=true",
        "mediflow.pharmacy.outbox.enabled=true",
        "mediflow.pharmacy.outbox.dispatch-delay-ms=600000",
        "mediflow.pharmacy.reservation.release-cron=-",
        "mediflow.pharmacy.reservation.reconciliation-cron=-",
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes"
})
@Testcontainers(disabledWithoutDocker = true)
class PharmacyRabbitIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @Autowired
    private PharmacyEventOutboxJpaRepository outbox;

    @Autowired
    private PharmacyOutboxDispatcher dispatcher;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    private String testQueue;

    /** Removes only the temporary queue and outbox rows created by this test class. */
    @AfterEach
    void clean() {
        outbox.deleteAllInBatch();
        if (testQueue != null) {
            rabbitAdmin.deleteQueue(testQueue);
        }
    }

    /** A broker ACK marks the outbox row published and delivers the immutable event payload. */
    @Test
    void dispatcher_ack_publishesAndMarksOutbox() {
        testQueue = "pharmacy.integration." + UUID.randomUUID();
        rabbitAdmin.declareQueue(QueueBuilder.durable(testQueue).build());
        rabbitAdmin.declareBinding(BindingBuilder.bind(new Queue(testQueue))
                .to(new TopicExchange(RabbitConfig.EVENTS_EXCHANGE))
                .with("prescription.created"));

        UUID eventId = UUID.randomUUID();
        PharmacyEventOutboxJpaEntity event = new PharmacyEventOutboxJpaEntity(
                eventId, "prescription.created", "{\"eventId\":\"" + eventId + "\"}");
        event.setCreatedAt(Instant.now());
        event.setAvailableAt(Instant.now());
        outbox.saveAndFlush(event);

        dispatcher.dispatchPending();

        assertThat(outbox.findById(eventId).orElseThrow().getPublishedAt()).isNotNull();
        org.springframework.amqp.core.Message delivered = rabbitTemplate.receive(testQueue, 5000);
        assertThat(delivered).isNotNull();
        assertThat(new String(delivered.getBody())).contains(eventId.toString());
    }

    /** An unroutable mandatory publish is returned and remains pending for retry. */
    @Test
    void dispatcher_mandatoryReturn_keepsOutboxPending() {
        UUID eventId = UUID.randomUUID();
        PharmacyEventOutboxJpaEntity event = new PharmacyEventOutboxJpaEntity(
                eventId, "prescription.integration.unroutable", "{\"eventId\":\"" + eventId + "\"}");
        event.setCreatedAt(Instant.now());
        event.setAvailableAt(Instant.now());
        outbox.saveAndFlush(event);

        dispatcher.dispatchPending();

        PharmacyEventOutboxJpaEntity stored = outbox.findById(eventId).orElseThrow();
        assertThat(stored.getPublishedAt()).isNull();
        assertThat(stored.getAttempts()).isGreaterThanOrEqualTo(1);
    }
}
