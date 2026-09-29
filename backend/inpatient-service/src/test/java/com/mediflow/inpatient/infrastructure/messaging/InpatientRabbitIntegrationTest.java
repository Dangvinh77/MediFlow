package com.mediflow.inpatient.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.mediflow.inpatient.application.dto.event.AdmissionStartedEvent;
import com.mediflow.inpatient.application.dto.event.DomainEventEnvelope;
import com.mediflow.inpatient.application.port.out.InpatientOutboxPort;
import com.mediflow.inpatient.infrastructure.messaging.config.InpatientConsumerConfiguration;
import com.mediflow.inpatient.infrastructure.messaging.consumer.InpatientEventConsumer;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies Inpatient delivery guarantees against real PostgreSQL and RabbitMQ instances. */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.rabbitmq.publisher-confirm-type=correlated",
        "spring.rabbitmq.publisher-returns=true",
        "spring.rabbitmq.template.mandatory=true",
        "mediflow.inpatient.messaging.producer.enabled=false",
        "mediflow.inpatient.messaging.consumers.enabled=true",
        "mediflow.jwt.secret=inpatient-rabbit-test-secret-at-least-32-bytes"
})
@Testcontainers(disabledWithoutDocker = true)
class InpatientRabbitIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @Autowired
    private InpatientOutboxPort outbox;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @SpyBean
    private InpatientEventConsumer consumer;

    private final List<String> temporaryQueues = new ArrayList<>();
    private InpatientOutboxDispatcher dispatcher;

    @BeforeEach
    void prepareBrokerAndDatabase() {
        dispatcher = new InpatientOutboxDispatcher(outbox, rabbitTemplate);
        jdbcTemplate.update("DELETE FROM su_kien_outbox_noi_tru");
        rabbitAdmin.purgeQueue(InpatientConsumerConfiguration.INPATIENT_QUEUE, false);
        rabbitAdmin.purgeQueue(InpatientConsumerConfiguration.DEAD_LETTER_QUEUE, false);
        clearInvocations(consumer);
    }

    @AfterEach
    void cleanTemporaryQueues() {
        for (String queue : temporaryQueues) {
            rabbitAdmin.deleteQueue(queue);
        }
        temporaryQueues.clear();
    }

    @Test
    void dispatcher_brokerAck_publishesPayloadAndMarksOutbox() {
        String routingKey = "inpatient.integration.published";
        String queue = declareBoundQueue(routingKey);
        UUID eventId = insertPendingEvent(routingKey);

        dispatcher.publishPending();

        Message delivered = rabbitTemplate.receive(queue, 5000);
        assertThat(delivered).isNotNull();
        assertThat(new String(delivered.getBody(), StandardCharsets.UTF_8)).contains(eventId.toString());
        assertThat(jdbcTemplate.queryForObject("""
                SELECT published_at IS NOT NULL
                FROM su_kien_outbox_noi_tru WHERE event_id = ?
                """, Boolean.class, eventId)).isTrue();
    }

    @Test
    void dispatcher_mandatoryReturn_keepsOutboxPendingForRetry() {
        UUID eventId = insertPendingEvent("inpatient.integration.unroutable." + UUID.randomUUID());

        dispatcher.publishPending();

        assertThat(jdbcTemplate.queryForObject("""
                SELECT published_at IS NULL
                FROM su_kien_outbox_noi_tru WHERE event_id = ?
                """, Boolean.class, eventId)).isTrue();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT retry_count
                FROM su_kien_outbox_noi_tru WHERE event_id = ?
                """, Integer.class, eventId)).isEqualTo(1);
    }

    @Test
    void broker_redeliversUnacknowledgedMessage_withRedeliveryFlag() {
        String queue = declareQueue("inpatient.redelivery." + UUID.randomUUID());
        String payload = "{\"eventId\":\"" + UUID.randomUUID() + "\"}";
        rabbitTemplate.send("", queue,
                MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8)).build());

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

    @Test
    void consumer_poisonMessage_retriesThreeTimesThenDeadLetters() {
        byte[] malformedPayload = "{".getBytes(StandardCharsets.UTF_8);
        rabbitTemplate.send(InpatientConsumerConfiguration.EVENTS_EXCHANGE, "admission.requested",
                MessageBuilder.withBody(malformedPayload).setContentType("application/json").build());

        verify(consumer, timeout(15000).times(3)).receive(any(Message.class));
        Message deadLetter = rabbitTemplate.receive(
                InpatientConsumerConfiguration.DEAD_LETTER_QUEUE, 10000);
        assertThat(deadLetter).isNotNull();
        assertThat(deadLetter.getBody()).isEqualTo(malformedPayload);
        assertThat(deadLetter.getMessageProperties().getXDeathHeader()).isNotEmpty();
    }

    private UUID insertPendingEvent(String eventType) {
        UUID eventId = UUID.randomUUID();
        UUID admissionId = UUID.randomUUID();
        Instant occurredAt = Instant.now();
        outbox.append(admissionId, new DomainEventEnvelope<>(eventId, eventType, 1, occurredAt,
                "corr-" + eventId, "inpatient-service",
                new AdmissionStartedEvent(admissionId, UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), occurredAt, false, null)));
        return eventId;
    }

    private String declareBoundQueue(String routingKey) {
        String queue = declareQueue("inpatient.integration." + UUID.randomUUID());
        rabbitAdmin.declareBinding(BindingBuilder.bind(new Queue(queue))
                .to(new TopicExchange(InpatientConsumerConfiguration.EVENTS_EXCHANGE))
                .with(routingKey));
        return queue;
    }

    private String declareQueue(String queue) {
        temporaryQueues.add(queue);
        rabbitAdmin.declareQueue(QueueBuilder.durable(queue).build());
        return queue;
    }

    private GetResponse basicGetEventually(Channel channel, String queue, boolean autoAck) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            try {
                GetResponse response = channel.basicGet(queue, autoAck);
                if (response != null) {
                    return response;
                }
                Thread.sleep(50);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Unable to read test queue " + queue, exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while polling test queue " + queue, exception);
            }
        }
        return null;
    }
}
