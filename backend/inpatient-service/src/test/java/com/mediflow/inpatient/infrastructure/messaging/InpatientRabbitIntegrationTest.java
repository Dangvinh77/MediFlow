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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
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

    private static final UUID SURGERY_CASE_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID SURGERY_PATIENT_ID = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private static final UUID SURGERY_DEPARTMENT_ID = UUID.fromString("00000000-0000-4000-8000-000000000004");
    private static final UUID SURGERY_ADMISSION_ID = UUID.fromString("00000000-0000-4000-8000-000000000008");

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
        rabbitAdmin.purgeQueue(InpatientConsumerConfiguration.INPATIENT_QUEUE, false);
        rabbitAdmin.purgeQueue(InpatientConsumerConfiguration.DEAD_LETTER_QUEUE, false);
        jdbcTemplate.update("DELETE FROM dien_bien_dieu_tri");
        jdbcTemplate.update("DELETE FROM tham_chieu_y_lenh");
        jdbcTemplate.update("DELETE FROM tiep_nhan_su_kien_phau_thuat");
        jdbcTemplate.update("DELETE FROM su_kien_da_xu_ly");
        jdbcTemplate.update("DELETE FROM su_kien_outbox_noi_tru");
        jdbcTemplate.update("DELETE FROM dot_noi_tru");
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

    @Test
    void consumer_surgeryEventFirstSequence_preservesTerminalStateUnderRedelivery() throws Exception {
        insertAdmittedSurgeryAdmission();

        publishSurgeryFixture("surgery.completed", "surgery.completed.admission.v1.json");
        awaitDatabase(() -> count("tiep_nhan_su_kien_phau_thuat") == 1
                && count("tham_chieu_y_lenh") == 0
                && count("dien_bien_dieu_tri") == 0,
                "early surgery completion was not retained without side effects");

        publishSurgeryFixture("surgery.case.created", "surgery.case.created.admission.v1.json");
        awaitDatabase(() -> count("tiep_nhan_su_kien_phau_thuat") == 2
                && count("tham_chieu_y_lenh") == 1
                && count("dien_bien_dieu_tri") == 1
                && "COMPLETED".equals(referenceStatus()),
                "retained surgery completion was not applied after reference registration");

        publishSurgeryFixture("surgery.ready", "surgery.ready.admission.v1.json");
        publishSurgeryFixture("surgery.completed", "surgery.completed.admission.v1.json");
        awaitDatabase(() -> count("tiep_nhan_su_kien_phau_thuat") == 3
                && count("dien_bien_dieu_tri") == 1
                && "COMPLETED".equals(referenceStatus()),
                "late readiness or redelivery changed the terminal surgery effect");

        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM tiep_nhan_su_kien_phau_thuat
                WHERE surgery_case_id = ? AND applied_at IS NOT NULL
                """, Integer.class, SURGERY_CASE_ID)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT content FROM dien_bien_dieu_tri
                WHERE admission_id = ? AND entry_type = 'SURGERY'
                """, String.class, SURGERY_ADMISSION_ID))
                .contains("Surgery completed");
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

    private void insertAdmittedSurgeryAdmission() {
        jdbcTemplate.update("""
                INSERT INTO dot_noi_tru(admission_id, admission_request_id, patient_id, source_record_id,
                    requested_by, diagnosis_summary, requested_at, department_id, priority, emergency,
                    status, admitted_at)
                VALUES (?, ?, ?, ?, ?, 'Surgery broker acceptance', now(), ?, 'ROUTINE', false,
                    'ADMITTED', now())
                """, SURGERY_ADMISSION_ID, UUID.randomUUID(), SURGERY_PATIENT_ID, UUID.randomUUID(),
                UUID.randomUUID(), SURGERY_DEPARTMENT_ID);
    }

    private void publishSurgeryFixture(String routingKey, String fixtureName) throws Exception {
        Path fixture = Path.of("../surgery-service/src/test/resources/contracts/surgery-outcomes-v1", fixtureName);
        rabbitTemplate.send(InpatientConsumerConfiguration.EVENTS_EXCHANGE, routingKey,
                MessageBuilder.withBody(Files.readAllBytes(fixture))
                        .setContentType("application/json")
                        .build());
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private String referenceStatus() {
        return jdbcTemplate.queryForObject("""
                SELECT status FROM tham_chieu_y_lenh
                WHERE order_type = 'SURGERY' AND external_order_id = ?
                """, String.class, SURGERY_CASE_ID);
    }

    private void awaitDatabase(BooleanSupplier condition, String failureMessage) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for database state", exception);
            }
        }
        throw new AssertionError(failureMessage);
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
