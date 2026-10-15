package com.mediflow.clinical.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.port.out.OutpatientMvpMode;
import com.mediflow.clinical.infrastructure.config.RabbitConfig;
import com.mediflow.clinical.infrastructure.persistence.jpaEntity.ClinicalOutboxEventJpaEntity;
import com.mediflow.clinical.infrastructure.persistence.repository.ClinicalOutboxJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real publisher confirmations/returns and committed outbox state; no business flow claim. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class ClinicalOutboxRabbitIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container
    static final RabbitMQContainer MQ = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @Autowired ClinicalOutboxJpaRepository outbox;
    @Autowired PlatformTransactionManager transactionManager;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private CachingConnectionFactory connection;
    private RabbitTemplate rabbit;
    private RabbitAdmin admin;
    private TransactionTemplate transactions;
    private String queue;

    @BeforeEach
    void setUp() {
        transactions = new TransactionTemplate(transactionManager);
        outbox.deleteAllInBatch();
        connection = new CachingConnectionFactory(MQ.getHost(), MQ.getAmqpPort());
        connection.setUsername(MQ.getAdminUsername());
        connection.setPassword(MQ.getAdminPassword());
        connection.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connection.setPublisherReturns(true);
        rabbit = new RabbitTemplate(connection);
        rabbit.setMandatory(true);
        admin = new RabbitAdmin(connection);
        admin.declareExchange(new TopicExchange(RabbitConfig.EVENTS_EXCHANGE, true, false));
        queue = "clinical-outbox-test-" + UUID.randomUUID();
        admin.declareQueue(new Queue(queue, true, false, false));
    }

    @AfterEach
    void tearDown() {
        if (admin != null) admin.deleteQueue(queue);
        if (connection != null) connection.destroy();
    }

    @Test
    void unroutableThenRecoveredRoute_retriesOriginalBytesAfterNewDispatcher() throws Exception {
        byte[] body = "{ \"eventId\" : \"retained-original-body\", \"value\" : 1 }\n"
                .getBytes(StandardCharsets.UTF_8);
        var event = save("appointment.created", body);

        dispatch(true, 3);
        assertThat(row(event).getPublishedAt()).isNull();
        assertThat(row(event).getRetryCount()).isOne();
        assertThat(row(event).getPayloadBytes()).isEqualTo(body);

        bind("appointment.created");
        // A new dispatcher/connection resumes the committed pending row, without rebuilding payload.
        connection.resetConnection();
        dispatch(true, 3);

        var delivered = rabbit.receive(queue, 5000);
        assertThat(delivered).isNotNull();
        assertThat(delivered.getBody()).isEqualTo(body);
        assertThat(delivered.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(row(event).getPublishedAt()).isNotNull();
        assertThat(row(event).getRetryCount()).isOne();
        dispatch(true, 3);
        assertThat(rabbit.receive(queue, 300)).isNull();
    }

    @Test
    void rollbackAfterConfirm_replaysOriginalBytesFromSameOutboxRow() throws Exception {
        byte[] body = "{ \"eventId\": \"rollback-window\" }\n".getBytes(StandardCharsets.UTF_8);
        var event = save("appointment.created", body);
        bind("appointment.created");
        transactions.executeWithoutResult(status -> {
            new ClinicalOutboxDispatcher(outbox, rabbit, Duration.ofSeconds(5), 3,
                    new OutpatientMvpMode(true)).dispatch();
            status.setRollbackOnly();
        });
        var first = rabbit.receive(queue, 5000);
        assertThat(first).isNotNull();
        assertThat(first.getBody()).isEqualTo(body);
        assertThat(row(event).getPublishedAt()).isNull();
        connection.resetConnection();
        dispatch(true, 3);
        var replay = rabbit.receive(queue, 5000);
        assertThat(replay).isNotNull();
        assertThat(replay.getBody()).isEqualTo(body);
        assertThat(row(event).getEventId()).isEqualTo(event.getEventId());
        assertThat(row(event).getPublishedAt()).isNotNull();
    }

    @Test
    void brokerApplicationRestart_retainsConfirmedMessageAndResumesPendingOriginalBytes() throws Exception {
        byte[] body = "{ \"eventId\" : \"broker-restart-window\" }\n".getBytes(StandardCharsets.UTF_8);
        var confirmed = save("appointment.created", body);
        bind("appointment.created");
        dispatch(true, 3);
        assertThat(row(confirmed).getPublishedAt()).isNotNull();
        assertThat(MQ.execInContainer("rabbitmqctl", "stop_app").getExitCode()).isZero();
        try {
            var pending = save("appointment.created", body);
            connection.resetConnection();
            dispatch(true, 3);
            assertThat(row(pending).getPublishedAt()).isNull();
            assertThat(row(pending).getRetryCount()).isOne();
            assertThat(MQ.execInContainer("rabbitmqctl", "start_app").getExitCode()).isZero();
            connection.resetConnection();
            var retained = rabbit.receive(queue, 10000);
            assertThat(retained).isNotNull();
            assertThat(retained.getBody()).isEqualTo(body);
            dispatch(true, 3);
            var resumed = rabbit.receive(queue, 5000);
            assertThat(resumed).isNotNull();
            assertThat(resumed.getBody()).isEqualTo(body);
            assertThat(row(pending).getPublishedAt()).isNotNull();
        } finally {
            MQ.execInContainer("rabbitmqctl", "start_app");
        }
    }

    @Test
    void boundedUnroutableFailures_quarantineSurvivesCompatibilityMode() throws Exception {
        var event = save("appointment.created", "{\"value\":1}".getBytes(StandardCharsets.UTF_8));
        dispatch(true, 2);
        dispatch(true, 2);
        assertThat(row(event).getPublishedAt()).isNull();
        assertThat(row(event).getRetryCount()).isEqualTo(2);
        assertThat(row(event).getQuarantinedAt()).isNotNull();
        bind("appointment.created");
        dispatch(false, 2);
        assertThat(rabbit.receive(queue, 300)).isNull();
        assertThat(row(event).getPublishedAt()).isNull();
        assertThat(row(event).getQuarantinedAt()).isNotNull();
    }

    @Test
    void deferredFact_isHeldInPostgresAndNotReleasedByCompatibilityDispatcher() throws Exception {
        byte[] body = "{\"payload\":{\"disposition\":\"ADMISSION\"}}".getBytes(StandardCharsets.UTF_8);
        var event = save("medicalrecord.completed", body);
        bind("medicalrecord.completed");
        dispatch(true, 3);
        assertThat(row(event).getHeldAt()).isNotNull();
        assertThat(row(event).getPublishedAt()).isNull();
        dispatch(false, 3);
        assertThat(rabbit.receive(queue, 300)).isNull();
        assertThat(row(event).getHeldAt()).isNotNull();
        assertThat(row(event).getPayloadBytes()).isEqualTo(body);
        assertThat(row(event).getRetryCount()).isZero();
    }

    private ClinicalOutboxEventJpaEntity save(String type, byte[] body) throws Exception {
        return outbox.saveAndFlush(ClinicalOutboxEventJpaEntity.builder()
                .eventId(UUID.randomUUID()).aggregateType("test").aggregateId(UUID.randomUUID())
                .eventType(type).eventVersion(1).correlationId("clinical-outbox-acceptance")
                .payload(mapper.readTree(body)).payloadBytes(body).occurredAt(Instant.now()).build());
    }

    private ClinicalOutboxEventJpaEntity row(ClinicalOutboxEventJpaEntity event) {
        return outbox.findById(event.getEventId()).orElseThrow();
    }

    private void dispatch(boolean outpatient, int retries) {
        var dispatcher = new ClinicalOutboxDispatcher(outbox, rabbit, Duration.ofSeconds(5), retries,
                new OutpatientMvpMode(outpatient));
        transactions.executeWithoutResult(status -> dispatcher.dispatch());
    }

    private void bind(String type) {
        admin.declareBinding(new Binding(queue, Binding.DestinationType.QUEUE,
                RabbitConfig.EVENTS_EXCHANGE, type, null));
    }
}
