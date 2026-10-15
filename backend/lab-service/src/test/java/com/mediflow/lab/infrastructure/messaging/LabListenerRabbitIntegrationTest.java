package com.mediflow.lab.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.lab.application.port.out.LabOutpatientMvpMode;
import com.mediflow.lab.application.service.ConsumerInboxBoundary;
import com.mediflow.lab.infrastructure.config.RabbitConfig;
import com.mediflow.lab.infrastructure.persistence.adapter.ConsumerInboxPersistenceAdapter;
import com.mediflow.lab.messaging.consumer.FinancialClearanceConsumer;
import com.mediflow.lab.messaging.consumer.LabQueueConsumer;
import com.mediflow.lab.messaging.consumer.MedicalRecordCreatedConsumer;
import com.mediflow.lab.messaging.consumer.PaymentCompletedConsumer;
import com.mediflow.lab.messaging.consumer.inbox.InboxEvidenceFactory;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Production listener/retry/inbox with a transactional SQL test effect, not a clinical projection E2E. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ConsumerInboxBoundary.class, ConsumerInboxPersistenceAdapter.class})
@Testcontainers
class LabListenerRabbitIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container
    static final RabbitMQContainer MQ = new RabbitMQContainer("rabbitmq:3.13-alpine");
    @Autowired ConsumerInboxBoundary inbox;
    @Autowired JdbcTemplate jdbc;
    private CachingConnectionFactory connection;
    private RabbitTemplate rabbit;
    private SimpleMessageListenerContainer listener;
    private final AtomicInteger attempts = new AtomicInteger();
    private volatile int failAttempts;
    private byte[] fixture;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("CREATE TABLE IF NOT EXISTS rabbit_test_effect (record_id UUID NOT NULL)");
        jdbc.execute("TRUNCATE rabbit_test_effect, consumer_inbox_delivery, consumer_inbox_operation");
        connection = new CachingConnectionFactory(MQ.getHost(), MQ.getAmqpPort());
        connection.setUsername(MQ.getAdminUsername());
        connection.setPassword(MQ.getAdminPassword());
        rabbit = new RabbitTemplate(connection);
        var config = new RabbitConfig();
        var admin = new RabbitAdmin(connection);
        admin.declareExchange(config.eventsExchange());
        admin.declareExchange(config.deadLetterExchange());
        admin.declareQueue(config.labQueue());
        admin.declareQueue(config.labDeadLetterQueue());
        admin.declareBinding(config.medicalRecordCreatedBinding(config.labQueue(), config.eventsExchange()));
        admin.declareBinding(config.paymentCompletedBinding(config.labQueue(), config.eventsExchange()));
        admin.declareBinding(config.labDeadLetterBinding(config.labDeadLetterQueue(), config.deadLetterExchange()));
        admin.purgeQueue(RabbitConfig.LAB_QUEUE);
        admin.purgeQueue(RabbitConfig.LAB_DEAD_LETTER_QUEUE);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var consumer = new LabQueueConsumer(mapper, new MedicalRecordCreatedConsumer(command -> {
            int attempt = attempts.incrementAndGet();
            jdbc.update("INSERT INTO rabbit_test_effect(record_id) VALUES (?)", command.recordId());
            if (attempt <= failAttempts) throw new TransientDataAccessResourceException("test transient failure");
        }), new PaymentCompletedConsumer(command -> { throw new AssertionError("Unexpected payment effect"); }),
                new FinancialClearanceConsumer(command -> { throw new AssertionError("Unexpected clearance effect"); }),
                new LabOutpatientMvpMode(true), inbox, new InboxEvidenceFactory(mapper));
        listener = config.rabbitListenerContainerFactory(connection, config.rabbitJsonMessageConverter(),
                config.labListenerRetryInterceptor()).createListenerContainer();
        listener.setQueueNames(RabbitConfig.LAB_QUEUE);
        listener.setPrefetchCount(1);
        listener.setMessageListener((MessageListener) consumer::consume);
        try (var input = getClass().getResourceAsStream("/contracts/medicalrecord.created.v1.json")) {
            assertThat(input).isNotNull();
            fixture = input.readAllBytes();
        }
        listener.start();
    }

    @AfterEach
    void tearDown() {
        if (listener != null) listener.destroy();
        if (connection != null) connection.destroy();
    }

    @Test
    void transientFailureRetriesThenCommitsInboxAndEffectTogether() {
        failAttempts = 2;
        send(RabbitConfig.MEDICAL_RECORD_CREATED, fixture);
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(count("rabbit_test_effect")).isOne();
            assertThat(count("consumer_inbox_delivery")).isOne();
        });
        assertThat(attempts.get()).isEqualTo(3);
        assertThat(count("consumer_inbox_operation")).isOne();
        assertThat(rabbit.receive(RabbitConfig.LAB_DEAD_LETTER_QUEUE, 300)).isNull();
        assertThat(jdbc.queryForObject("SELECT raw_body FROM consumer_inbox_delivery", byte[].class))
                .isEqualTo(fixture);
    }

    @Test
    void exhaustedTransientFailureGoesToDlqAndRollsBackEveryAttempt() {
        failAttempts = Integer.MAX_VALUE;
        send(RabbitConfig.MEDICAL_RECORD_CREATED, fixture);
        assertDeadLetter(fixture);
        assertThat(attempts.get()).isEqualTo(3);
        assertNoCommittedEffects();
    }

    @Test
    void duplicateAfterListenerRestartHasOneEffectAndConflictingBytesGoToDlq() {
        send(RabbitConfig.MEDICAL_RECORD_CREATED, fixture);
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(count("consumer_inbox_delivery")).isOne());
        listener.stop();
        connection.resetConnection();
        send(RabbitConfig.MEDICAL_RECORD_CREATED, fixture);
        byte[] conflict = new String(fixture, StandardCharsets.UTF_8)
                .replace("OUTPATIENT_EXAM", "CONFLICTING_PRICE_CODE").getBytes(StandardCharsets.UTF_8);
        send(RabbitConfig.MEDICAL_RECORD_CREATED, conflict);
        listener.start();
        assertDeadLetter(conflict);
        assertThat(attempts.get()).isOne();
        assertThat(count("rabbit_test_effect")).isOne();
        assertThat(count("consumer_inbox_delivery")).isOne();
        assertThat(count("consumer_inbox_operation")).isOne();
    }

    @Test
    void malformedJsonAndQuarantinedPaymentGoToDlqWithoutInboxOrEffect() {
        byte[] poison = "{invalid".getBytes(StandardCharsets.UTF_8);
        send(RabbitConfig.MEDICAL_RECORD_CREATED, poison);
        assertDeadLetter(poison);
        byte[] payment = "{\"eventId\":\"must-not-be-processed\"}".getBytes(StandardCharsets.UTF_8);
        send(RabbitConfig.PAYMENT_COMPLETED, payment);
        assertDeadLetter(payment);
        assertThat(attempts.get()).isZero();
        assertNoCommittedEffects();
    }

    private void send(String routingKey, byte[] body) {
        var properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbit.send(RabbitConfig.EVENTS_EXCHANGE, routingKey, new Message(body, properties));
    }

    private void assertDeadLetter(byte[] body) {
        Message dead = rabbit.receive(RabbitConfig.LAB_DEAD_LETTER_QUEUE, 10000);
        assertThat(dead).isNotNull();
        assertThat(dead.getBody()).isEqualTo(body);
        assertThat(dead.getMessageProperties().getHeaders()).containsKey("x-death");
        assertThat(rabbit.receive(RabbitConfig.LAB_DEAD_LETTER_QUEUE, 300)).isNull();
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private void assertNoCommittedEffects() {
        assertThat(count("rabbit_test_effect")).isZero();
        assertThat(count("consumer_inbox_delivery")).isZero();
        assertThat(count("consumer_inbox_operation")).isZero();
    }
}
