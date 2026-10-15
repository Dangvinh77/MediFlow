package com.mediflow.clinical.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;
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

/** Kills the real packaged service after confirmed publish but before the outbox transaction commits. */
@Testcontainers
class OutboxProcessRecoveryIT {
    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container
    static final RabbitMQContainer MQ = new RabbitMQContainer("rabbitmq:3.13-alpine");
    private static final long BARRIER_KEY = 419901;
    @TempDir(cleanup = CleanupMode.ON_SUCCESS) Path logs;

    @Test
    void killedPackagedJvmReplaysUncommittedPublicationThenStopsRepublishing() throws Exception {
        Path jar = Path.of(System.getProperty("recovery.jar")).toAbsolutePath();
        assertThat(jar).as("Executable JAR from this Maven package phase").isRegularFile();
        var dataSource = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        byte[] body;
        try (var input = getClass().getResourceAsStream("/contracts/appointment.status.changed.v1.json")) {
            assertThat(input).isNotNull();
            body = input.readAllBytes();
        }
        var json = new ObjectMapper().readTree(body);
        UUID eventId = UUID.fromString(json.path("eventId").asText());
        String routingKey = json.path("eventType").asText();
        seed(jdbc, eventId, routingKey, json.toString(), body);
        // Isolated test-only SQL barrier: the real dispatcher has published and is waiting to mark the row.
        jdbc.execute("""
                CREATE FUNCTION recovery_commit_barrier() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.published_at IS NOT NULL THEN
                        PERFORM pg_advisory_xact_lock(419901);
                    END IF;
                    RETURN NEW;
                END $$;
                CREATE TRIGGER recovery_commit_barrier BEFORE UPDATE ON clinical_outbox_event
                FOR EACH ROW EXECUTE FUNCTION recovery_commit_barrier();
                """);
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
            String queue = "clinical-process-recovery";
            admin.declareExchange(new TopicExchange("mediflow.events", true, false));
            admin.declareQueue(new Queue(queue, true, false, false));
            admin.declareBinding(new Binding(queue, Binding.DestinationType.QUEUE,
                    "mediflow.events", routingKey, null));

            first = start(jar, "before-kill.log");
            Message initial = rabbit.receive(queue, 90000);
            assertThat(initial).as("Packaged JVM published before commit; see %s", logs).isNotNull();
            assertPayload(initial, body);
            await().atMost(Duration.ofSeconds(45)).untilAsserted(() ->
                    assertThat(Files.readString(logs.resolve("before-kill.log"))
                            .contains("Started ClinicalServiceApplication"))
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
            assertThat(jdbc.queryForObject("SELECT count(*) FROM clinical_outbox_event", Integer.class)).isOne();
            try (var statement = barrier.createStatement()) {
                statement.execute("SELECT pg_advisory_unlock(" + BARRIER_KEY + ")");
            }

            restarted = start(jar, "after-restart.log");
            assertThat(restarted.pid()).isNotEqualTo(firstPid);
            Message replay = rabbit.receive(queue, 90000);
            assertThat(replay).as("Same JAR resumed committed pending row; see %s", logs).isNotNull();
            assertThat(replay.getBody()).isEqualTo(initial.getBody());
            assertPayload(replay, body);
            await().atMost(Duration.ofSeconds(45)).untilAsserted(() ->
                    assertThat(Files.readString(logs.resolve("after-restart.log"))
                            .contains("Started ClinicalServiceApplication"))
                            .as("Restarted service fully started; see %s", logs).isTrue());
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                    assertThat(published(jdbc, eventId)).isTrue());
            assertThat(restarted.isAlive()).isTrue();
            // Poll interval is 500 ms; several subsequent polls must not republish a committed row.
            assertThat(rabbit.receive(queue, 2000)).isNull();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM clinical_outbox_event", Integer.class)).isOne();
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
    }

    private void seed(JdbcTemplate jdbc, UUID eventId, String type, String json, byte[] body) {
        jdbc.update("""
                INSERT INTO clinical_outbox_event(event_id,aggregate_type,aggregate_id,event_type,event_version,
                    correlation_id,payload,payload_bytes,occurred_at)
                VALUES (?,'recovery-test',?,?,1,'process-recovery',?::jsonb,?,now())
                """, eventId, UUID.randomUUID(), type, json, body);
    }

    private void assertPayload(Message delivered, byte[] original) throws Exception {
        assertThat(delivered.getBody()).isEqualTo(original);
    }

    private boolean published(JdbcTemplate jdbc, UUID eventId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT published_at IS NOT NULL FROM clinical_outbox_event WHERE event_id = ?", Boolean.class, eventId));
    }

    private Process start(Path jar, String logName) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        List<String> command = new ArrayList<>(List.of(java, "-Xmx256m", "-jar", jar.toString(),
                "--spring.profiles.active=outpatient-mvp", "--server.port=0",
                "--spring.datasource.url=" + PG.getJdbcUrl(),
                "--spring.datasource.username=" + PG.getUsername(),
                "--spring.rabbitmq.host=" + MQ.getHost(),
                "--spring.rabbitmq.port=" + MQ.getAmqpPort(),
                "--spring.rabbitmq.username=" + MQ.getAdminUsername(),
                "--eureka.client.enabled=false", "--spring.cloud.discovery.enabled=false",
                "--mediflow.clinical.outbox-poll-interval=500", "--spring.main.banner-mode=off"));
        var builder = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(logs.resolve(logName).toFile());
        // Test-only credentials stay out of command arguments and the repository.
        builder.environment().put("SPRING_DATASOURCE_PASSWORD", PG.getPassword());
        builder.environment().put("SPRING_RABBITMQ_PASSWORD", MQ.getAdminPassword());
        builder.environment().put("MEDIFLOW_JWT_SECRET",
                "process-recovery-test-only-" + UUID.randomUUID() + UUID.randomUUID());
        builder.environment().put("MEDIFLOW_CARE_FINANCE_V2_ENABLED", "false");
        builder.environment().put("MEDIFLOW_CARE_FINANCE_V2", "false");
        return builder.start();
    }

    private void kill(Process process) throws InterruptedException {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            assertThat(process.waitFor(15, TimeUnit.SECONDS)).as("Child JVM terminated").isTrue();
        }
    }
}
