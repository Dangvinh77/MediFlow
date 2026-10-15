package com.mediflow.lab.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.lab.infrastructure.config.RabbitConfig;
import com.mediflow.lab.infrastructure.persistence.jpaEntity.LabOutboxEventJpaEntity;
import com.mediflow.lab.infrastructure.persistence.repository.LabOutboxEventJpaRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
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

/** Real outbox transactions and broker recovery, independent of cross-owner business flows. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class LabOutboxRabbitIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container
    static final RabbitMQContainer MQ = new RabbitMQContainer("rabbitmq:3.13-alpine");
    @Autowired LabOutboxEventJpaRepository outbox;
    @Autowired PlatformTransactionManager transactionManager;
    private CachingConnectionFactory connection;
    private RabbitTemplate rabbit;
    private RabbitAdmin admin;
    private TransactionTemplate transactions;
    private String queue;

    @BeforeEach
    void setUp() {
        outbox.deleteAllInBatch();
        transactions = new TransactionTemplate(transactionManager);
        connection = new CachingConnectionFactory(MQ.getHost(), MQ.getAmqpPort());
        connection.setUsername(MQ.getAdminUsername());
        connection.setPassword(MQ.getAdminPassword());
        connection.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connection.setPublisherReturns(true);
        rabbit = new RabbitTemplate(connection);
        rabbit.setMandatory(true);
        admin = new RabbitAdmin(connection);
        admin.declareExchange(new TopicExchange(RabbitConfig.EVENTS_EXCHANGE, true, false));
        queue = "lab-outbox-test-" + UUID.randomUUID();
        admin.declareQueue(new Queue(queue, true, false, false));
    }

    @AfterEach
    void tearDown() {
        if (admin != null) admin.deleteQueue(queue);
        if (connection != null) connection.destroy();
    }

    @Test
    void unroutableEventRemainsPendingThenNewDispatcherResumesSameIdentityAndPayload() throws Exception {
        var event = save();
        dispatch(false);
        assertThat(row(event).getPublishedAt()).isNull();
        assertThat(row(event).getRetryCount()).isOne();
        bind();
        connection.resetConnection();
        dispatch(false);
        var delivered = rabbit.receive(queue, 5000);
        assertEvent(delivered, event);
        assertThat(row(event).getPublishedAt()).isNotNull();
        assertThat(row(event).getRetryCount()).isOne();
        dispatch(false);
        assertThat(rabbit.receive(queue, 300)).isNull();
    }

    @Test
    void rollbackAfterBrokerConfirmLeavesPendingRowAndReplaysSameMessage() throws Exception {
        var event = save();
        bind();
        dispatch(true);
        var beforeRollback = rabbit.receive(queue, 5000);
        assertEvent(beforeRollback, event);
        assertThat(row(event).getPublishedAt()).isNull();
        connection.resetConnection();
        dispatch(false);
        var replay = rabbit.receive(queue, 5000);
        assertEvent(replay, event);
        assertThat(replay.getBody()).isEqualTo(beforeRollback.getBody());
        assertThat(row(event).getPublishedAt()).isNotNull();
    }

    @Test
    void brokerApplicationRestartRetainsConfirmedPersistentMessageAndResumesPendingRow() throws Exception {
        var confirmed = save();
        bind();
        dispatch(false);
        assertThat(row(confirmed).getPublishedAt()).isNotNull();
        assertThat(MQ.execInContainer("rabbitmqctl", "stop_app").getExitCode()).isZero();
        try {
            var pending = save();
            connection.resetConnection();
            dispatch(false);
            assertThat(row(pending).getPublishedAt()).isNull();
            assertThat(row(pending).getRetryCount()).isOne();
            assertThat(MQ.execInContainer("rabbitmqctl", "start_app").getExitCode()).isZero();
            connection.resetConnection();
            assertEvent(rabbit.receive(queue, 10000), confirmed);
            dispatch(false);
            assertEvent(rabbit.receive(queue, 5000), pending);
            assertThat(row(pending).getPublishedAt()).isNotNull();
        } finally {
            MQ.execInContainer("rabbitmqctl", "start_app");
        }
    }

    private LabOutboxEventJpaEntity save() throws Exception {
        UUID id = UUID.randomUUID();
        return outbox.saveAndFlush(LabOutboxEventJpaEntity.builder()
                .eventId(id).aggregateType("test").aggregateId(UUID.randomUUID())
                .eventType("lab.result.created").eventVersion(1).correlationId("lab-recovery-test")
                .payload(new ObjectMapper().readTree("{\"eventId\":\"" + id + "\",\"value\":1}"))
                .occurredAt(Instant.now()).build());
    }

    private LabOutboxEventJpaEntity row(LabOutboxEventJpaEntity event) {
        return outbox.findById(event.getEventId()).orElseThrow();
    }

    private void dispatch(boolean rollback) {
        transactions.executeWithoutResult(status -> {
            new LabOutboxDispatcher(outbox, rabbit, 5000).dispatchPending();
            if (rollback) status.setRollbackOnly();
        });
    }

    private void bind() {
        admin.declareBinding(new Binding(queue, Binding.DestinationType.QUEUE,
                RabbitConfig.EVENTS_EXCHANGE, "lab.result.created", null));
    }

    private void assertEvent(Message delivered, LabOutboxEventJpaEntity event) throws Exception {
        assertThat(delivered).isNotNull();
        assertThat(new ObjectMapper().readTree(delivered.getBody())).isEqualTo(event.getPayload());
        assertThat(delivered.getMessageProperties().getMessageId()).isEqualTo(event.getEventId().toString());
        assertThat(delivered.getMessageProperties().getCorrelationId()).isEqualTo(event.getCorrelationId());
        assertThat(delivered.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
    }
}
