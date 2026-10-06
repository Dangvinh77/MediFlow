package com.mediflow.surgery.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryEventPublisherPort;
import com.mediflow.surgery.application.port.out.SurgeryOutboxPort;
import com.mediflow.surgery.infrastructure.persistence.SurgeryOutboxAdapter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.DockerClientFactory;
import static org.awaitility.Awaitility.await;

@Testcontainers
class SurgeryOutboxDispatchRecoveryIntegrationTest {

    private static final Instant INITIAL_TIME = Instant.parse("2026-09-29T07:00:00Z");
    private static final String EXCHANGE = "mediflow.surgery.recovery.test";
    private static final String ROUTING_KEY = "surgery.transport.test";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("surgery_outbox_recovery")
            .withUsername("surgery")
            .withPassword("surgery");

    @Container
    private static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");

    private static JdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static SurgeryOutboxPort outbox;

    private CachingConnectionFactory connectionFactory;
    private RabbitTemplate rabbitTemplate;
    private String queueName;

    @BeforeAll
    static void migrateDatabase() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load().migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        outbox = new SurgeryOutboxAdapter(jdbc);
    }

    @BeforeEach
    void configureRabbit() {
        jdbc.execute("TRUNCATE surgery_case CASCADE");
        connectionFactory = new CachingConnectionFactory(RABBIT.getHost(), currentRabbitPort());
        connectionFactory.setUsername(RABBIT.getAdminUsername());
        connectionFactory.setPassword(RABBIT.getAdminPassword());
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connectionFactory.setPublisherReturns(true);
        connectionFactory.setConnectionTimeout(1000);
        rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMandatory(true);

        queueName = "surgery-retry-" + UUID.randomUUID();
        TopicExchange exchange = new TopicExchange(EXCHANGE, true, false);
        RabbitAdmin admin = new RabbitAdmin(rabbitTemplate);
        admin.declareExchange(exchange);
        admin.declareQueue(new Queue(queueName, true, false, false));
        admin.declareBinding(BindingBuilder.bind(new Queue(queueName, true, false, false))
                .to(exchange).with(ROUTING_KEY));
    }

    @AfterEach
    void closeRabbitConnectionFactory() {
        if (connectionFactory != null) {
            try { new RabbitAdmin(rabbitTemplate).deleteQueue(queueName); }
            finally { connectionFactory.destroy(); }
        }
    }

    @Test
    void unavailableRabbitEndpoint_persistsRetryAndPublishesAfterWorkerRestart() throws IOException {
        UUID caseId = insertCase();
        UUID eventId = UUID.randomUUID();
        byte[] payload = "{ \"transport\" : \"stored-bytes\", \"version\": 1 }"
                .getBytes(StandardCharsets.UTF_8);
        transactions.executeWithoutResult(ignored -> outbox.append(new SurgeryOutboxPort.OutgoingEvent(
                eventId, caseId, 1, 0, ROUTING_KEY, 1,
                "recovery-correlation", payload, INITIAL_TIME)));

        AtomicReference<Instant> now = new AtomicReference<>(INITIAL_TIME);
        SurgeryClockPort clock = now::get;
        int unavailablePort = unusedLocalPort();
        CachingConnectionFactory unavailableConnectionFactory =
                new CachingConnectionFactory("127.0.0.1", unavailablePort);
        unavailableConnectionFactory.setConnectionTimeout(1000);
        RabbitTemplate unavailableTemplate = new RabbitTemplate(unavailableConnectionFactory);
        RabbitSurgeryEventPublisherAdapter unavailablePublisher =
                new RabbitSurgeryEventPublisherAdapter(unavailableTemplate, EXCHANGE, 1000);
        RabbitSurgeryEventPublisherAdapter rabbitPublisher =
                new RabbitSurgeryEventPublisherAdapter(rabbitTemplate, EXCHANGE, 5000);

        try {
            SurgeryOutboxDispatcher firstWorker = dispatcher(unavailablePublisher, clock);
            assertThat(firstWorker.dispatchOne()).isTrue();
        } finally {
            unavailableConnectionFactory.destroy();
        }
        assertThat(outboxRow(eventId))
                .containsEntry("status", "PENDING")
                .containsEntry("attempt_count", 1)
                .containsEntry("reason", "RabbitMQ publish failed");
        assertThat(jdbc.queryForObject(
                "SELECT next_attempt_at FROM surgery_outbox WHERE event_id = ?",
                Timestamp.class, eventId).toInstant()).isEqualTo(INITIAL_TIME.plusSeconds(2));

        // A new dispatcher instance models a restarted worker; the durable row remains the source of truth.
        now.set(INITIAL_TIME.plusSeconds(3));
        SurgeryOutboxDispatcher restartedWorker = dispatcher(rabbitPublisher, clock);
        assertThat(restartedWorker.dispatchOne()).isTrue();

        Message delivered = rabbitTemplate.receive(queueName, 5000);
        assertThat(delivered).isNotNull();
        assertThat(delivered.getBody()).containsExactly(payload);
        assertThat(delivered.getMessageProperties().getCorrelationId()).isEqualTo("recovery-correlation");
        assertThat(outboxRow(eventId))
                .containsEntry("status", "PUBLISHED")
                .containsEntry("attempt_count", 2);
    }

    private static SurgeryOutboxDispatcher dispatcher(
            SurgeryEventPublisherPort publisher, SurgeryClockPort clock) {
        return new SurgeryOutboxDispatcher(
                transactions, outbox, publisher, clock, Duration.ofSeconds(30), 10);
    }

    @Test
    void stoppedBrokerContainer_retainsPendingBytesAndRecoversAfterActualRestart() throws Exception {
        UUID eventId = UUID.randomUUID();
        byte[] payload = "{\"transport\":\"broker-restart\",\"version\":1}".getBytes(StandardCharsets.UTF_8);
        append(eventId, payload);
        AtomicReference<Instant> now = new AtomicReference<>(INITIAL_TIME);
        var publisher = new RabbitSurgeryEventPublisherAdapter(rabbitTemplate, EXCHANGE, 1000);
        var docker = DockerClientFactory.instance().client();
        // Only this test's isolated Testcontainers broker is stopped; no user-owned broker is touched.
        docker.stopContainerCmd(RABBIT.getContainerId()).withTimeout(2).exec();
        try {
            assertThat(dispatcher(publisher, now::get).dispatchOne()).isTrue();
            assertThat(outboxRow(eventId)).containsEntry("status", "PENDING").containsEntry("attempt_count", 1);
            assertThat(jdbc.queryForObject("SELECT payload FROM surgery_outbox WHERE event_id = ?", byte[].class, eventId))
                    .containsExactly(payload);
        } finally {
            docker.startContainerCmd(RABBIT.getContainerId()).exec();
            await().atMost(Duration.ofSeconds(45)).pollInterval(Duration.ofSeconds(1)).ignoreExceptions()
                    .until(() -> RABBIT.execInContainer("rabbitmq-diagnostics", "-q", "check_running").getExitCode() == 0);
            connectionFactory.resetConnection();
            // Docker can allocate a new random host port for the same restarted test container.
            connectionFactory.setPort(currentRabbitPort());
            await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200)).ignoreExceptions()
                    .untilAsserted(() -> assertThat(new RabbitAdmin(rabbitTemplate).getQueueProperties(queueName)).isNotNull());
        }
        now.set(INITIAL_TIME.plusSeconds(3));
        assertThat(dispatcher(publisher, now::get).dispatchOne()).isTrue();
        Message delivered = rabbitTemplate.receive(queueName, 5000);
        assertThat(delivered).isNotNull();
        assertThat(delivered.getBody()).containsExactly(payload);
        assertThat(delivered.getMessageProperties().getMessageId()).isEqualTo(eventId.toString());
        assertThat(outboxRow(eventId)).containsEntry("status", "PUBLISHED").containsEntry("attempt_count", 2);
    }

    @Test
    void brokerConfirmedBeforeWorkerCrash_leaseReplayKeepsIdentityAndFencesOldAttempt() {
        UUID eventId = UUID.randomUUID();
        byte[] payload = "{ \"transport\" : \"confirmed-before-crash\" }".getBytes(StandardCharsets.UTF_8);
        append(eventId, payload);
        var original = transactions.execute(ignored -> outbox.claimNext(INITIAL_TIME, Duration.ofSeconds(30)).orElseThrow());
        var publisher = new RabbitSurgeryEventPublisherAdapter(rabbitTemplate, EXCHANGE, 5000);
        publisher.publish(new SurgeryEventPublisherPort.OutgoingMessage(original.eventId(), original.eventType(),
                original.eventVersion(), original.correlationId(), original.payload()));
        // Broker has confirmed, but the process dies before marking PUBLISHED in a separate DB transaction.
        assertThat(outboxRow(eventId)).containsEntry("status", "CLAIMED");
        var restartTime = INITIAL_TIME.plusSeconds(31);
        assertThat(dispatcher(publisher, () -> restartTime).dispatchOne()).isTrue();
        Boolean staleCompletion = transactions.execute(ignored -> outbox.markPublished(eventId, original.attemptToken(), restartTime));
        assertThat(staleCompletion).isFalse();
        for (int copy = 0; copy < 2; copy++) {
            Message delivered = rabbitTemplate.receive(queueName, 5000);
            assertThat(delivered).isNotNull();
            assertThat(delivered.getBody()).containsExactly(payload);
            assertThat(delivered.getMessageProperties().getMessageId()).isEqualTo(eventId.toString());
        }
        assertThat(rabbitTemplate.receive(queueName, 200)).isNull();
        assertThat(outboxRow(eventId)).containsEntry("status", "PUBLISHED").containsEntry("attempt_count", 2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM surgery_outbox WHERE event_id = ?", Integer.class, eventId)).isEqualTo(1);
    }

    private static void append(UUID eventId, byte[] payload) {
        UUID caseId = insertCase();
        transactions.executeWithoutResult(ignored -> outbox.append(new SurgeryOutboxPort.OutgoingEvent(
                eventId, caseId, 1, 0, ROUTING_KEY, 1, "recovery-correlation", payload, INITIAL_TIME)));
    }

    private static int currentRabbitPort() {
        var bindings = DockerClientFactory.instance().client().inspectContainerCmd(RABBIT.getContainerId()).exec()
                .getNetworkSettings().getPorts().getBindings().get(com.github.dockerjava.api.model.ExposedPort.tcp(5672));
        return Integer.parseInt(bindings[0].getHostPortSpec());
    }

    private static java.util.Map<String, Object> outboxRow(UUID eventId) {
        return jdbc.queryForMap("SELECT status, attempt_count, reason FROM surgery_outbox WHERE event_id = ?", eventId);
    }

    private static int unusedLocalPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static UUID insertCase() {
        UUID caseId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO surgery_case
                (surgery_case_id, surgery_request_id, episode_type,
                 episode_id, patient_id, department_id, requested_by,
                 procedure_code, indication, priority, status, requested_at)
                VALUES (?, ?, 'OUTPATIENT_VISIT', ?, ?, ?, ?, 'PROC', 'Transport recovery test',
                        'ROUTINE', 'REQUESTED', ?)
                """, caseId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), Timestamp.from(INITIAL_TIME));
        return caseId;
    }
}
