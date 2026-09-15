package com.mediflow.pharmacy.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.MessageBuilder;
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
import com.mediflow.pharmacy.infrastructure.persistence.jpaentity.PharmacyEventOutboxJpaEntity;
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
        "mediflow.pharmacy.outbox.dispatch-initial-delay-ms=600000",
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

    private final List<String> testQueues = new ArrayList<>();

    /** Removes only the temporary queue and outbox rows created by this test class. */
    @AfterEach
    void clean() {
        outbox.deleteAllInBatch();
        for (String queue : testQueues) {
            rabbitAdmin.deleteQueue(queue);
        }
        testQueues.clear();
    }

    /** A broker ACK marks the outbox row published and delivers the immutable event payload. */
    @Test
    void dispatcher_ack_publishesAndMarksOutbox() {
        String testQueue = registerQueue("pharmacy.integration." + UUID.randomUUID());
        rabbitAdmin.declareQueue(QueueBuilder.durable(testQueue).build());
        rabbitAdmin.declareBinding(BindingBuilder.bind(new Queue(testQueue))
                .to(new TopicExchange(RabbitConfig.EVENTS_EXCHANGE))
                .with("prescription.created"));

        UUID eventId = UUID.randomUUID();
        PharmacyEventOutboxJpaEntity event = new PharmacyEventOutboxJpaEntity(
                eventId, "prescription.created", UUID.randomUUID(),
                "{\"eventId\":\"" + eventId + "\"}");
        event.setCreatedAt(Instant.now());
        event.setAvailableAt(Instant.now());
        outbox.saveAndFlush(event);

        dispatcher.dispatchPending();

        assertThat(outbox.findById(eventId).orElseThrow().getPublishedAt()).isNotNull();
        org.springframework.amqp.core.Message delivered = rabbitTemplate.receive(testQueue, 5000);
        assertThat(delivered).isNotNull();
        assertThat(new String(delivered.getBody(), StandardCharsets.UTF_8)).contains(eventId.toString());
    }

    /** A consumer crash before acknowledgement causes RabbitMQ to redeliver the same message. */
    @Test
    void broker_redeliversUnacknowledgedMessage_withRedeliveryFlag() {
        String queue = registerQueue("pharmacy.redelivery." + UUID.randomUUID());
        rabbitAdmin.declareQueue(QueueBuilder.durable(queue).build());
        String payload = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"reason\":\"hết hạn\"}";
        rabbitTemplate.send("", queue, MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8)).build());

        rabbitTemplate.execute(channel -> {
            GetResponse first = basicGetEventually(channel, queue, false);
            assertThat(first).isNotNull();
            assertThat(first.getEnvelope().isRedeliver()).isFalse();
            channel.basicReject(first.getEnvelope().getDeliveryTag(), true);
            GetResponse redelivered = basicGetEventually(channel, queue, false);
            assertThat(redelivered).isNotNull();
            assertThat(redelivered.getEnvelope().isRedeliver()).isTrue();
            assertThat(new String(redelivered.getBody(), StandardCharsets.UTF_8)).isEqualTo(payload);
            channel.basicAck(redelivered.getEnvelope().getDeliveryTag(), false);
            return null;
        });
    }

    /** A poison message rejected without requeue is routed by RabbitMQ to the configured DLQ. */
    @Test
    void broker_routesRejectedMessage_toDeadLetterQueue() {
        String deadLetterExchange = "pharmacy.dlx." + UUID.randomUUID();
        String deadLetterQueue = registerQueue("pharmacy.dlq." + UUID.randomUUID());
        String sourceQueue = registerQueue("pharmacy.source." + UUID.randomUUID());
        String routingKey = "poison";
        rabbitAdmin.declareExchange(new TopicExchange(deadLetterExchange));
        rabbitAdmin.declareQueue(QueueBuilder.durable(deadLetterQueue).build());
        rabbitAdmin.declareBinding(BindingBuilder.bind(new Queue(deadLetterQueue))
                .to(new TopicExchange(deadLetterExchange)).with(routingKey));
        rabbitAdmin.declareQueue(QueueBuilder.durable(sourceQueue)
                .withArgument("x-dead-letter-exchange", deadLetterExchange)
                .withArgument("x-dead-letter-routing-key", routingKey)
                .build());

        String payload = "{\"eventId\":\"" + UUID.randomUUID()
                + "\",\"reason\":\"sai dữ liệu\",\"poison\":true}";
        rabbitTemplate.send("", sourceQueue,
                MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8)).build());
        rabbitTemplate.execute(channel -> {
            GetResponse failed = basicGetEventually(channel, sourceQueue, false);
            assertThat(failed).isNotNull();
            channel.basicReject(failed.getEnvelope().getDeliveryTag(), false);
            return null;
        });

        GetResponse deadLetter = basicGetEventually(deadLetterQueue, true);
        assertThat(deadLetter).isNotNull();
        assertThat(new String(deadLetter.getBody(), StandardCharsets.UTF_8)).isEqualTo(payload);
    }

    private String registerQueue(String queue) {
        testQueues.add(queue);
        return queue;
    }

    /** Polls a real broker queue briefly to absorb publisher/consumer scheduling latency. */
    private GetResponse basicGetEventually(String queue, boolean autoAck) {
        return rabbitTemplate.execute(channel -> basicGetEventually(channel, queue, autoAck));
    }

    /** Polls a specific channel so delivery tags remain valid for ACK/reject operations. */
    private GetResponse basicGetEventually(Channel channel, String queue, boolean autoAck) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            GetResponse response;
            try {
                response = channel.basicGet(queue, autoAck);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Unable to read test queue " + queue, exception);
            }
            if (response != null) {
                return response;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while polling test queue " + queue, exception);
            }
        }
        return null;
    }

    /** An unroutable mandatory publish is returned and remains pending for retry. */
    @Test
    void dispatcher_mandatoryReturn_keepsOutboxPending() {
        UUID eventId = UUID.randomUUID();
        PharmacyEventOutboxJpaEntity event = new PharmacyEventOutboxJpaEntity(
                eventId, "prescription.integration.unroutable", UUID.randomUUID(),
                "{\"eventId\":\"" + eventId + "\"}");
        event.setCreatedAt(Instant.now());
        event.setAvailableAt(Instant.now());
        outbox.saveAndFlush(event);

        dispatcher.dispatchPending();

        PharmacyEventOutboxJpaEntity stored = outbox.findById(eventId).orElseThrow();
        assertThat(stored.getPublishedAt()).isNull();
        assertThat(stored.getAttempts()).isGreaterThanOrEqualTo(1);
    }

}
