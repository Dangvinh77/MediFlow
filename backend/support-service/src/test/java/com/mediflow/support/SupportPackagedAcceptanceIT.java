package com.mediflow.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Real executable, existing stores upgraded from standalone migrations, restart after an accepted write. */
@Testcontainers
class SupportPackagedAcceptanceIT {
    @Container static final PostgreSQLContainer<?> REPORT = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final PostgreSQLContainer<?> NOTIFICATION = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final RabbitMQContainer MQ = new RabbitMQContainer("rabbitmq:3.13-alpine");
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @Test void packagedHostPreservesStandaloneHistoryAndAcceptedWritesAcrossRestart() throws Exception {
        Path jar = Path.of("target/support-service-0.0.1-SNAPSHOT.jar").toAbsolutePath();
        try (var archive = new JarFile(jar.toFile())) {
            assertThat(archive.getManifest().getMainAttributes().getValue("Start-Class"))
                    .isEqualTo("com.mediflow.support.SupportServiceApplication");
            assertThat(archive.stream().map(e -> e.getName()).toList())
                    .contains("BOOT-INF/lib/report-service-0.0.1-SNAPSHOT-lib.jar", "BOOT-INF/lib/notification-service-0.0.1-SNAPSHOT-lib.jar");
        }
        // Apply actual legacy locations first. Host startup must validate the same histories/checksums.
        var reportHistory = Flyway.configure().dataSource(REPORT.getJdbcUrl(), REPORT.getUsername(), REPORT.getPassword())
                .locations("filesystem:../report-service/src/main/resources/db/migration").load();
        var notificationHistory = Flyway.configure().dataSource(NOTIFICATION.getJdbcUrl(), NOTIFICATION.getUsername(), NOTIFICATION.getPassword())
                .locations("filesystem:../notification-service/src/main/resources/db/migration").load();
        reportHistory.migrate(); notificationHistory.migrate();
        var reportBefore = reportHistory.info().applied(); var notificationBefore = notificationHistory.info().applied();
        int port; try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        Process process = start(jar, port, "first");
        UUID id;
        try {
            healthy(port, process);
            var response = send(port, "POST", "/api/v1/notifications/send", mapper.writeValueAsString(Map.of(
                    "patientId", SupportSecurityTest.PATIENT.toString(), "title", "Support restart test",
                    "content", "Isolated acceptance data", "channel", "IN_APP")), SupportSecurityTest.token("ADMIN", "access"));
            assertThat(response.statusCode()).isEqualTo(201);
            id = UUID.fromString(mapper.readTree(response.body()).path("data").path("notificationId").asText());
            assertThat(send(port, "GET", "/api/v1/reports/daily?date=2026-10-17", null, SupportSecurityTest.token("MANAGER", "access")).statusCode()).isEqualTo(200);
        } finally { stop(process); }
        process = start(jar, port, "restart");
        try {
            healthy(port, process);
            var response = send(port, "GET", "/api/v1/notifications/" + id, null, SupportSecurityTest.token("PATIENT", "access"));
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(response.body()).path("data").path("notificationId").asText()).isEqualTo(id.toString());
            assertThat(reportHistory.info().applied()).extracting(i -> i.getChecksum()).containsExactly(
                    java.util.Arrays.stream(reportBefore).map(i -> i.getChecksum()).toArray(Integer[]::new));
            assertThat(notificationHistory.info().applied()).extracting(i -> i.getChecksum()).containsExactly(
                    java.util.Arrays.stream(notificationBefore).map(i -> i.getChecksum()).toArray(Integer[]::new));
        } finally { stop(process); }
    }

    private Process start(Path jar, int port, String label) throws Exception {
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Xmx256m", "-jar", jar.toString(),
                "--server.port=" + port, "--eureka.client.enabled=false",
                "--mediflow.features.care-finance-v2=false",
                "--mediflow.notification.care-v1.enabled=false",
                "--mediflow.notification.outpatient-prescription-consumer.enabled=false",
                "--mediflow.notification.refund-consumer.enabled=false",
                "--mediflow.report.outpatient-consumer.enabled=false");
        var env = builder.environment();
        env.put("MEDIFLOW_REPORT_DB_URL", REPORT.getJdbcUrl()); env.put("MEDIFLOW_REPORT_DB_USER", REPORT.getUsername()); env.put("MEDIFLOW_REPORT_DB_PASSWORD", REPORT.getPassword());
        env.put("MEDIFLOW_NOTIFICATION_DB_URL", NOTIFICATION.getJdbcUrl()); env.put("MEDIFLOW_NOTIFICATION_DB_USER", NOTIFICATION.getUsername()); env.put("MEDIFLOW_NOTIFICATION_DB_PASSWORD", NOTIFICATION.getPassword());
        env.put("MEDIFLOW_RABBIT_HOST", MQ.getHost()); env.put("MEDIFLOW_RABBIT_PORT", MQ.getAmqpPort().toString());
        env.put("MEDIFLOW_RABBIT_USER", MQ.getAdminUsername()); env.put("MEDIFLOW_RABBIT_PASSWORD", MQ.getAdminPassword());
        env.put("MEDIFLOW_JWT_SECRET", SupportSecurityTest.SECRET);
        return builder.redirectErrorStream(true).redirectOutput(Path.of("target/support-packaged-" + label + ".log").toFile()).start();
    }
    private void healthy(int port, Process process) {
        await().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofMillis(500))
                .ignoreExceptionsMatching(error -> error instanceof java.net.ConnectException
                        || error instanceof java.net.http.HttpConnectTimeoutException).untilAsserted(() -> {
            assertThat(process.isAlive()).isTrue();
            assertThat(send(port, "GET", "/actuator/health", null, null).statusCode()).isEqualTo(200);
        });
    }
    private HttpResponse<String> send(int port, String method, String path, String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(5));
        if (token != null) request.header("Authorization", token);
        if (body != null) request.header("Content-Type", "application/json");
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private static void stop(Process process) throws Exception {
        process.destroy();
        if (!process.waitFor(15, TimeUnit.SECONDS)) { process.destroyForcibly(); assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue(); }
    }
}
