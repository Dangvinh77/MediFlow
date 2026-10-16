package com.mediflow.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * L3 recovery proof (HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md §5, L3): kills the real
 * packaged billing-service after the broker confirmed publish but before the outbox transaction
 * marking {@code published_at} commits. Mirrors clinical-service's own {@code OutboxProcessRecoveryIT}
 * barrier technique, against {@code BILLING_EVENT_OUTBOX} instead. Opt-in via the
 * {@code outpatient-publication-acceptance} Maven profile; needs a packaged jar (run after
 * {@code mvn package}), Docker and Java 21.
 */
@Testcontainers
class OutpatientPublicationProcessRecoveryIT {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final RabbitMQContainer MQ = new RabbitMQContainer("rabbitmq:3.13-alpine");
    private static final long BARRIER_KEY = 419902;
    @TempDir(cleanup = CleanupMode.ON_SUCCESS) Path logs;

    @Test
    void killedPackagedJvmReplaysUncommittedEligiblePublicationThenStopsRepublishing() throws Exception {
        Path jar = Path.of(System.getProperty("recovery.jar")).toAbsolutePath();
        assertThat(jar).as("Executable JAR from this Maven package phase").isRegularFile();
        var dataSource = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        UUID eventId = UUID.randomUUID();
        UUID account = seedEnrolledEligibleEvent(jdbc, eventId);
        byte[] body = jdbc.queryForObject("SELECT payload FROM BILLING_EVENT_OUTBOX WHERE event_id=?", String.class, eventId)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // Isolated test-only SQL barrier: the real dispatcher has published and is waiting to mark the row.
        jdbc.execute("""
                CREATE FUNCTION recovery_commit_barrier() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.published_at IS NOT NULL THEN
                        PERFORM pg_advisory_xact_lock(419902);
                    END IF;
                    RETURN NEW;
                END $$;
                CREATE TRIGGER recovery_commit_barrier BEFORE UPDATE ON BILLING_EVENT_OUTBOX
                FOR EACH ROW WHEN (NEW.event_id = '%s') EXECUTE FUNCTION recovery_commit_barrier();
                """.formatted(eventId));

        Process first = null;
        Process restarted = null;
        var connection = new CachingConnectionFactory(MQ.getHost(), MQ.getAmqpPort());
        connection.setUsername(MQ.getAdminUsername());
        connection.setPassword(MQ.getAdminPassword());
        try (Connection barrier = dataSource.getConnection()) {
            try (var statement = barrier.createStatement()) {
                statement.execute("SELECT pg_advisory_lock(" + BARRIER_KEY + ")");
            }
            var rabbit = new RabbitTemplate(connection);
            var admin = new RabbitAdmin(connection);
            String queue = "billing-process-recovery";
            admin.declareExchange(new TopicExchange("mediflow.events", true, false));
            admin.declareQueue(new Queue(queue, true, false, false));
            admin.declareBinding(new Binding(queue, Binding.DestinationType.QUEUE, "mediflow.events", "payment.completed", null));

            first = start(jar, "before-kill.log");
            Message initial = rabbit.receive(queue, 90000);
            assertThat(initial).as("Packaged JVM published before commit; see %s", logs).isNotNull();
            assertThat(initial.getBody()).isEqualTo(body);
            await().atMost(Duration.ofSeconds(45)).untilAsserted(() ->
                    assertThat(Files.readString(logs.resolve("before-kill.log"))
                            .contains("Started BillingServiceApplication"))
                            .as("Packaged service fully started; see %s", logs).isTrue());
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                    assertThat(jdbc.queryForObject("""
                            SELECT count(*) FROM pg_stat_activity
                            WHERE datname = current_database() AND wait_event = 'advisory'
                            """, Integer.class)).isGreaterThan(0));
            assertThat(first.isAlive()).isTrue();
            long firstPid = first.pid();
            kill(first);
            assertThat(published(jdbc, eventId)).isFalse();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM BILLING_EVENT_OUTBOX WHERE event_id=?", Integer.class, eventId)).isOne();
            try (var statement = barrier.createStatement()) {
                statement.execute("SELECT pg_advisory_unlock(" + BARRIER_KEY + ")");
            }

            restarted = start(jar, "after-restart.log");
            assertThat(restarted.pid()).isNotEqualTo(firstPid);
            Message replay = rabbit.receive(queue, 90000);
            assertThat(replay).as("Same JAR resumed committed pending row; see %s", logs).isNotNull();
            assertThat(replay.getBody()).isEqualTo(initial.getBody());
            assertThat(replay.getBody()).isEqualTo(body);
            await().atMost(Duration.ofSeconds(45)).untilAsserted(() ->
                    assertThat(Files.readString(logs.resolve("after-restart.log"))
                            .contains("Started BillingServiceApplication"))
                            .as("Restarted service fully started; see %s", logs).isTrue());
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(published(jdbc, eventId)).isTrue());
            assertThat(restarted.isAlive()).isTrue();
            // Poll interval is fast in this profile; several subsequent polls must not republish a committed row.
            assertThat(rabbit.receive(queue, 2000)).isNull();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM BILLING_EVENT_OUTBOX WHERE event_id=?", Integer.class, eventId)).isOne();
            // Not an admin replay; eventId/bytes are unchanged and no second financial operation exists.
            assertThat(jdbc.queryForObject(
                    "SELECT payload FROM BILLING_EVENT_OUTBOX WHERE event_id=?", String.class, eventId).getBytes(
                    java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(body);
        } finally {
            try {
                kill(first);
            } finally {
                try {
                    kill(restarted);
                } finally {
                    connection.destroy();
                }
            }
        }
        awaitLogRelease("before-kill.log");
        awaitLogRelease("after-restart.log");
    }

    /**
     * Seeds exactly what L1/L2 production code would have produced for a brand-new enrolled EXAM
     * request: account, request, charge, cohort row, a completed payment transaction, and one
     * eligible {@code payment.completed} outbox row — without going through the full consumer/HTTP
     * flow, since only dispatcher/process recovery is under test here. Every field the V23
     * {@code billing_outpatient_event_matches} fence cross-checks must be real and consistent, the
     * same as it would be for a genuine payment: a hand-written payload alone is now rejected.
     */
    private UUID seedEnrolledEligibleEvent(JdbcTemplate jdbc, UUID eventId) {
        UUID account = UUID.randomUUID(), request = UUID.randomUUID(), invoice = UUID.randomUUID();
        UUID patient = UUID.randomUUID(), department = UUID.randomUUID(), appointment = UUID.randomUUID();
        UUID transaction = UUID.randomUUID(), charge = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO BILLING_ACCOUNT(account_id,patient_id,department_id,care_episode_type,care_episode_id,currency,opened_at)
                VALUES (?,?,?,'OUTPATIENT_VISIT',?,'VND',now())
                """, account, patient, department, appointment);
        jdbc.update("INSERT INTO PAYMENT_REQUEST(payment_request_id,invoice_id,account_id,purpose,status,requested_amount,currency) "
                + "VALUES (?,?,?,'EXAM','PAID',?,'VND')", request, invoice, account, new BigDecimal("100000.00"));
        jdbc.update("INSERT INTO PAYMENT_REQUEST_TARGET(payment_request_id,appointment_id) VALUES (?,?)", request, appointment);
        jdbc.update("""
                INSERT INTO CHARGE(charge_id,account_id,patient_id,department_id,source_type,source_id,price_code,
                    description,quantity,unit_amount,gross_amount,incurred_at) VALUES (?,?,?,?,'EXAM',?,'EXAM','exam',1,?,?,now())
                """, charge, account, patient, department, appointment, new BigDecimal("100000.00"), new BigDecimal("100000.00"));
        jdbc.update("INSERT INTO PAYMENT_REQUEST_CHARGE(payment_request_id,charge_id,requested_amount) VALUES (?,?,?)",
                request, charge, new BigDecimal("100000.00"));
        jdbc.update("""
                INSERT INTO PAYMENT_TRANSACTION(transaction_id,account_id,payment_request_id,transaction_type,classification,
                    status,amount,currency,payment_method,idempotency_key,completed_at)
                VALUES (?,?,?,'PAYMENT','SERVICE_PAYMENT','COMPLETED',?,'VND','CASH',?,now())
                """, transaction, account, request, new BigDecimal("100000.00"), UUID.randomUUID().toString());
        jdbc.update("""
                INSERT INTO billing_outpatient_publication_cohort(payment_request_id,account_id,purpose,source_id,acceptance_reference)
                VALUES (?,?,'EXAM',?,'L3-RECOVERY-RUN')
                """, request, account, appointment);
        String payload = """
                {"eventId":"%s","eventType":"payment.completed","version":1,"occurredAt":"2026-10-16T08:00:00Z",\
                "correlationId":"recovery-test","producer":"billing-service","payload":{"transactionId":"%s",\
                "invoiceId":"%s","paymentRequestId":"%s","accountId":"%s","patientId":"%s","departmentId":"%s",\
                "careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"%s","classification":"SERVICE_PAYMENT",\
                "totalAmount":100000.00,"currency":"VND","paymentMethod":"CASH","completedAt":"2026-10-16T08:00:00Z",\
                "prescriptionId":null,"labTestIds":[]}}
                """.formatted(eventId, transaction, invoice, request, account, patient, department, appointment).strip();
        jdbc.update("""
                INSERT INTO BILLING_EVENT_OUTBOX(event_id,routing_key,aggregate_id,payload,publication_enabled,contract_version,outpatient_payment_request_id)
                VALUES (?,'payment.completed',?,?,TRUE,1,?)
                """, eventId, account, payload, request);
        return account;
    }

    private void awaitLogRelease(String name) {
        Path log = logs.resolve(name);
        // Windows can retain a redirected file handle briefly after waitFor reports process exit.
        await().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(15))
                .ignoreExceptionsInstanceOf(IOException.class).until(() -> {
                    Files.move(log, logs.resolve(name + ".released"));
                    return true;
                });
    }

    private boolean published(JdbcTemplate jdbc, UUID eventId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT published_at IS NOT NULL FROM BILLING_EVENT_OUTBOX WHERE event_id = ?", Boolean.class, eventId));
    }

    private Process start(Path jar, String logName) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        List<String> command = new ArrayList<>(List.of(java, "-Xmx256m", "-jar", jar.toString(),
                "--server.port=0", "--eureka.client.enabled=false", "--spring.cloud.discovery.enabled=false",
                "--spring.datasource.url=" + PG.getJdbcUrl(),
                "--spring.datasource.username=" + PG.getUsername(),
                "--spring.rabbitmq.host=" + MQ.getHost(),
                "--spring.rabbitmq.port=" + MQ.getAmqpPort(),
                "--spring.rabbitmq.username=" + MQ.getAdminUsername(),
                "--mediflow.billing.ledger.enabled=true",
                "--mediflow.billing.outbox.enabled=true",
                "--mediflow.billing.outbox.metrics-enabled=false",
                "--mediflow.billing.outbox.maintenance-enabled=false",
                "--mediflow.billing.outbox.dispatch-delay-ms=500",
                "--mediflow.billing.outbox.dispatch-initial-delay-ms=500",
                "--spring.main.banner-mode=off"));
        var builder = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(logs.resolve(logName).toFile());
        // Test-only credentials stay out of command arguments and the repository.
        builder.environment().put("SPRING_DATASOURCE_PASSWORD", PG.getPassword());
        builder.environment().put("SPRING_RABBITMQ_PASSWORD", MQ.getAdminPassword());
        builder.environment().put("MEDIFLOW_JWT_SECRET",
                "process-recovery-test-only-" + UUID.randomUUID() + UUID.randomUUID());
        return builder.start();
    }

    private void kill(Process process) throws InterruptedException {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            assertThat(process.waitFor(15, TimeUnit.SECONDS)).as("Child JVM terminated").isTrue();
        }
    }
}
