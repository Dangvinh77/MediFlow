package com.mediflow.clinical.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Actual Clinical attachment/inbox commit followed by JVM kill before the Rabbit ACK. */
@Testcontainers
class ConsumerProcessRecoveryIT {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final RabbitMQContainer MQ = new RabbitMQContainer("rabbitmq:3.13-alpine");
    @TempDir(cleanup = CleanupMode.ON_SUCCESS) Path work;

    @ParameterizedTest
    @ValueSource(strings = {"LAB", "PRESCRIPTION"})
    void committedAttachmentSurvivesConsumerJvmDeathAndExactBrokerRedelivery(String type) throws Exception {
        Path jar = Path.of(System.getProperty("recovery.jar")).toAbsolutePath();
        assertThat(jar).isRegularFile();
        Path fixtureClasses = copyTestLauncherOnly();
        var source = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("TRUNCATE consumer_inbox_delivery, consumer_inbox_operation, attached_result, diagnosis, clinical_lab_order, medical_record, appointment CASCADE");
        byte[] body;
        if ("PRESCRIPTION".equals(type)) {
            body = Files.readAllBytes(Path.of("../pharmacy-service/src/test/resources/contracts/care-finance-v1/prescription.filled.outpatient.v1.json"));
        } else {
            try (var input = getClass().getResourceAsStream("/contracts/lab.result.created.v1.json")) {
                assertThat(input).isNotNull();
                body = input.readAllBytes();
            }
        }
        var root = new ObjectMapper().readTree(body);
        var payload = root.path("payload");
        UUID eventId = UUID.fromString(root.path("eventId").asText());
        UUID recordId = UUID.fromString(payload.path("recordId").asText());
        UUID patientId = UUID.fromString(payload.path("patientId").asText());
        UUID departmentId = UUID.fromString(payload.path("departmentId").asText());
        UUID appointmentId = UUID.fromString(payload.path("careEpisodeId").asText());
        UUID referenceId = UUID.fromString(payload.path("LAB".equals(type) ? "labId" : "prescriptionId").asText());
        // Seed only the Clinical-owned target of the canonical producer fixture; no foreign store is queried.
        UUID doctorId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO appointment(appointment_id,patient_id,doctor_id,department_id,
                    appointment_date,appointment_time,status)
                VALUES (?,?,?,?,CURRENT_DATE,'09:00','ARRIVED')
                """, appointmentId, patientId, doctorId, departmentId);
        jdbc.update("""
                INSERT INTO medical_record(record_id,patient_id,doctor_id,department_id,
                    examination_date,appointment_id,symptoms)
                VALUES (?,?,?,?,CURRENT_DATE,?,'Recovery fixture')
                """, recordId, patientId, doctorId, departmentId, appointmentId);

        var connection = new CachingConnectionFactory(MQ.getHost(), MQ.getAmqpPort());
        connection.setUsername(MQ.getAdminUsername());
        connection.setPassword(MQ.getAdminPassword());
        connection.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.SIMPLE);
        Process first = null;
        Process second = null;
        try {
            var rabbit = new RabbitTemplate(connection);
            var admin = new RabbitAdmin(connection);
            first = start(jar, fixtureClasses, "first", true);
            started("first");
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                    assertThat(admin.getQueueInfo("clinical.q")).isNotNull());
            var properties = new MessageProperties();
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setMessageId(eventId.toString());
            properties.setCorrelationId(root.path("correlationId").asText());
            rabbit.invoke(operations -> {
                operations.send("mediflow.events", root.path("eventType").asText(), new Message(body, properties));
                operations.waitForConfirmsOrDie(10000);
                return null;
            });
            delivered("first", body, false);
            assertCommitted(jdbc, eventId, recordId, referenceId, type, body);
            assertQueueState(0, 1);
            long pid = first.pid();
            kill(first);
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertQueueState(1, 0));
            assertCommitted(jdbc, eventId, recordId, referenceId, type, body);

            second = start(jar, fixtureClasses, "second", false);
            assertThat(second.pid()).isNotEqualTo(pid);
            started("second");
            delivered("second", body, true);
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertQueueState(0, 0));
            assertCommitted(jdbc, eventId, recordId, referenceId, type, body);
            assertThat(admin.getQueueInfo("clinical.dlq").getMessageCount()).isZero();
        } finally {
            try { kill(first); }
            finally {
                try { kill(second); }
                finally { connection.destroy(); }
            }
        }
    }

    // Retain child logs outside JUnit's temporary directory: Windows can briefly lock
    // redirected output after the child exits. Process termination is still asserted.
    private Path processLog(String name) throws java.io.IOException {
        Path logs = Path.of("target", "acceptance-logs", work.getFileName().toString());
        Files.createDirectories(logs);
        return logs.resolve(name + ".log");
    }

    private void assertCommitted(JdbcTemplate jdbc, UUID eventId, UUID recordId, UUID referenceId, String type, byte[] body) {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consumer_inbox_delivery", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consumer_inbox_operation", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT raw_body FROM consumer_inbox_delivery WHERE event_id=?",
                byte[].class, eventId)).isEqualTo(body);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM attached_result
                WHERE record_id=? AND type=? AND reference_id=?
                """, Integer.class, recordId, type, referenceId)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM attached_result", Integer.class)).isOne();
    }

    private void assertQueueState(int ready, int unacked) throws Exception {
        var result = MQ.execInContainer("rabbitmqctl", "list_queues", "name", "messages_ready",
                "messages_unacknowledged", "--formatter=json");
        assertThat(result.getExitCode()).isZero();
        var queues = new ObjectMapper().readTree(result.getStdout());
        boolean found = false;
        for (var queue : queues) {
            if ("clinical.q".equals(queue.path("name").asText())) {
                assertThat(queue.path("messages_ready").asInt()).isEqualTo(ready);
                assertThat(queue.path("messages_unacknowledged").asInt()).isEqualTo(unacked);
                found = true;
            }
        }
        assertThat(found).as("Clinical queue exists").isTrue();
    }

    private Path copyTestLauncherOnly() throws Exception {
        Path compiled = Path.of(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        Path packagePath = Path.of("com", "mediflow", "acceptance", "fixture");
        Path destination = work.resolve("test-launcher");
        Files.createDirectories(destination.resolve(packagePath));
        try (var classes = Files.list(compiled.resolve(packagePath))) {
            for (Path file : classes.filter(p -> p.getFileName().toString().startsWith("ConsumerRecoveryProcess")
                    && p.toString().endsWith(".class")).toList()) {
                Files.copy(file, destination.resolve(packagePath).resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        assertThat(destination.resolve(packagePath).resolve("ConsumerRecoveryProcess.class")).isRegularFile();
        return destination;
    }

    private Process start(Path jar, Path fixtureClasses, String name, boolean holdAck) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        var command = new ArrayList<>(List.of(java, "-Xmx256m",
                "-Dloader.main=com.mediflow.acceptance.fixture.ConsumerRecoveryProcess",
                "-Dloader.path=" + fixtureClasses, "-cp", jar.toString(),
                "org.springframework.boot.loader.launch.PropertiesLauncher",
                "--spring.profiles.active=outpatient-mvp", "--server.port=0",
                "--spring.datasource.url=" + PG.getJdbcUrl(), "--spring.datasource.username=" + PG.getUsername(),
                "--spring.rabbitmq.host=" + MQ.getHost(), "--spring.rabbitmq.port=" + MQ.getAmqpPort(),
                "--spring.rabbitmq.username=" + MQ.getAdminUsername(), "--eureka.client.enabled=false",
                "--spring.cloud.discovery.enabled=false", "--spring.main.banner-mode=off"));
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(processLog(name).toFile());
        builder.environment().put("SPRING_DATASOURCE_PASSWORD", PG.getPassword());
        builder.environment().put("SPRING_RABBITMQ_PASSWORD", MQ.getAdminPassword());
        builder.environment().put("MEDIFLOW_JWT_SECRET", "consumer-recovery-test-only-" + UUID.randomUUID());
        builder.environment().put("MEDIFLOW_CARE_FINANCE_V2_ENABLED", "false");
        builder.environment().put("RECOVERY_DELIVERY_MARKER", work.resolve(name + ".body").toString());
        builder.environment().put("RECOVERY_HOLD_ACK", Boolean.toString(holdAck));
        return builder.start();
    }

    private void started(String name) {
        await().atMost(Duration.ofSeconds(90)).untilAsserted(() ->
                assertThat(Files.readString(processLog(name)))
                        .as("Child fully started; failure logs retained at %s", work)
                        .contains("Started ConsumerRecoveryProcess"));
    }

    private void delivered(String name, byte[] original, boolean redelivered) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(work.resolve(name + ".body")).isRegularFile();
            assertThat(Files.readAllBytes(work.resolve(name + ".body"))).isEqualTo(original);
            assertThat(Files.readString(work.resolve(name + ".body.redelivered"))).isEqualTo(Boolean.toString(redelivered));
        });
    }

    private void kill(Process process) throws InterruptedException {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            assertThat(process.waitFor(15, TimeUnit.SECONDS)).as("Child JVM terminated").isTrue();
        }
    }
}
