package com.mediflow.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ConnectionFactory;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Operator-invoked acceptance on cold copies of the user's stopped development volumes. */
class HistoricalSupportCutoverIT {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final List<Process> children = new ArrayList<>();
    private final Map<Process, String> modules = new LinkedHashMap<>();
    private final Path evidence = Path.of(required("H6_REHEARSAL_DIRECTORY"));
    private final String pgPort = required("H6_REHEARSAL_PG_PORT");
    private String rabbitPort = required("H6_REHEARSAL_RABBIT_PORT");

    @Test void historicalCloneRetainsOldAndNewWritesThroughCutoverRollbackAndBrokerRestart() throws Exception {
        JsonNode manifest = JSON.readTree(evidence.resolve("source-manifest.json").toFile());
        assertThat(manifest.path("pgCloneVolume").asText()).startsWith("mediflow-h6-");
        assertThat(manifest.path("rabbitCloneVolume").asText()).startsWith("mediflow-h6-");
        assertThat(manifest.path("sourcePgVolume").asText()).isNotEqualTo(manifest.path("pgCloneVolume").asText());
        assertThat(manifest.path("sourceRabbitVolume").asText()).isNotEqualTo(manifest.path("rabbitCloneVolume").asText());
        assertThat(required("H6_REHEARSAL_PG_CONTAINER")).isEqualTo(manifest.path("pgContainer").asText());
        assertThat(required("H6_REHEARSAL_RABBIT_CONTAINER")).isEqualTo(manifest.path("rabbitContainer").asText());
        // These are explicitly local development clone credentials; never read deployment secrets.
        var factory = new ConnectionFactory();
        factory.setHost("127.0.0.1"); factory.setPort(Integer.parseInt(rabbitPort));
        factory.setUsername("guest"); factory.setPassword("guest");
        factory.setAutomaticRecoveryEnabled(false);
        String queue = "h6.rehearsal.retention." + UUID.randomUUID();
        byte[] marker = ("H6 clone retention probe " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        try (var connection = factory.newConnection(); var channel = connection.createChannel()) {
            channel.queueDeclare(queue, true, false, false, null);
            channel.confirmSelect();
            channel.basicPublish("", queue, true,
                    new AMQP.BasicProperties.Builder().deliveryMode(2).contentType("text/plain").build(), marker);
            channel.waitForConfirmsOrDie(10_000);
        }
        List<String> notices = new ArrayList<>();
        Map<String, String> noticeHashes = new LinkedHashMap<>();
        String reportBaseline;
        String reportHistory;
        String notificationHistory;
        try {
            int oldReport = port(), oldNotification = port();
            Process report = start("report-service", oldReport, "legacy-before-report");
            Process notification = start("notification-service", oldNotification, "legacy-before-notification");
            healthy(report, oldReport); healthy(notification, oldNotification);
            notices.add(write(oldNotification, "before-cutover"));
            stop(report); stop(notification);
            reportBaseline = snapshot("mediflow_report", false);
            reportHistory = history("mediflow_report");
            notificationHistory = history("mediflow_notification");
            captureNotices(noticeHashes, notices);

            int hostPort = port();
            Process support = start("support-service", hostPort, "support-cutover");
            healthy(support, hostPort); verify(hostPort, notices); assertReport(hostPort);
            notices.add(write(hostPort, "after-cutover"));
            stop(support);
            assertThat(snapshot("mediflow_report", false)).isEqualTo(reportBaseline);
            assertThat(history("mediflow_report")).isEqualTo(reportHistory);
            assertThat(history("mediflow_notification")).isEqualTo(notificationHistory);
            preserveNotices(noticeHashes); captureNotices(noticeHashes, notices);
            assertMarker(factory, queue, marker);

            // The original broker volume is never passed to Docker here. Restart only the validated copy.
            Process restart = new ProcessBuilder("docker", "restart", required("H6_REHEARSAL_RABBIT_CONTAINER"))
                    .redirectErrorStream(true).redirectOutput(evidence.resolve("broker-restart.log").toFile()).start();
            assertThat(restart.waitFor(60, TimeUnit.SECONDS)).isTrue();
            assertThat(restart.exitValue()).isZero();
            // Docker Desktop can allocate a new ephemeral host port after container restart.
            Process mapping = new ProcessBuilder("docker", "port", required("H6_REHEARSAL_RABBIT_CONTAINER"), "5672/tcp")
                    .redirectErrorStream(true).start();
            String address = new String(mapping.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            assertThat(mapping.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(mapping.exitValue()).isZero();
            assertThat(address).matches("127\\.0\\.0\\.1:[0-9]+");
            rabbitPort = address.substring(address.lastIndexOf(':') + 1);
            factory.setPort(Integer.parseInt(rabbitPort));
            await().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofSeconds(1)).ignoreExceptions()
                    .untilAsserted(() -> assertMarker(factory, queue, marker));

            oldReport = port(); oldNotification = port();
            report = start("report-service", oldReport, "legacy-rollback-report");
            notification = start("notification-service", oldNotification, "legacy-rollback-notification");
            healthy(report, oldReport); healthy(notification, oldNotification);
            verify(oldNotification, notices);
            assertReport(oldReport);
            notices.add(write(oldNotification, "during-rollback"));
            stop(report); stop(notification);
            preserveNotices(noticeHashes); captureNotices(noticeHashes, notices);

            hostPort = port();
            support = start("support-service", hostPort, "support-return");
            healthy(support, hostPort); verify(hostPort, notices); assertReport(hostPort);
            notices.add(write(hostPort, "after-return"));
            verify(hostPort, notices);
            stop(support);
            assertThat(snapshot("mediflow_report", false)).isEqualTo(reportBaseline);
            assertThat(history("mediflow_report")).isEqualTo(reportHistory);
            assertThat(history("mediflow_notification")).isEqualTo(notificationHistory);
            preserveNotices(noticeHashes); captureNotices(noticeHashes, notices);
            assertMarker(factory, queue, marker);
            Map<String, Object> proof = new LinkedHashMap<>();
            proof.put("completedAt", Instant.now().toString());
            proof.put("sourceHead", manifest.path("sourceHead").asText());
            proof.put("sourceManifestSha256", sha(Files.readAllBytes(evidence.resolve("source-manifest.json"))));
            proof.put("phases", List.of("legacy", "support", "broker-restart", "legacy-rollback", "support-return"));
            proof.put("notificationIds", notices); proof.put("notificationRowSha256", noticeHashes);
            proof.put("reportRowsSha256", reportBaseline);
            proof.put("reportHistorySha256", reportHistory); proof.put("notificationHistorySha256", notificationHistory);
            proof.put("retainedProbeQueue", queue); proof.put("retainedProbeBodySha256", sha(marker));
            proof.put("originalVolumesMountedReadOnly", true);
            proof.put("activationGates", "OFF");
            proof.put("historicalScope", "actual stopped local development volumes; source inventory records empty Report/Notification stores");
            JSON.writerWithDefaultPrettyPrinter().writeValue(evidence.resolve("cutover-proof.json").toFile(), proof);
        } finally {
            for (Process process : children) stop(process);
        }
    }

    private Process start(String module, int port, String label) throws Exception {
        // A standalone pair is allowed, but Support and legacy writers must never overlap.
        assertThat(modules.entrySet().stream().filter(e -> e.getKey().isAlive()).map(Map.Entry::getValue))
                .noneMatch(active -> active.equals(module) || active.equals("support-service") || module.equals("support-service"));
        return launch(module, port, label);
    }

    private Process launch(String module, int port, String label) throws Exception {
        Path jar = Path.of("..", module, "target", module + "-0.0.1-SNAPSHOT.jar").toAbsolutePath();
        assertThat(jar).isRegularFile();
        var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx256m", "-jar", jar.toString(), "--server.port=" + port, "--eureka.client.enabled=false",
                "--mediflow.features.care-finance-v2=false", "--spring.rabbitmq.port=" + rabbitPort,
                "--mediflow.report.outpatient-consumer.enabled=false", "--mediflow.report.outpatient-read.enabled=false",
                "--mediflow.notification.care-v1.enabled=false", "--mediflow.notification.outpatient-prescription-consumer.enabled=false",
                "--mediflow.notification.refund-consumer.enabled=false", "--spring.flyway.baseline-on-migrate=false"));
        if (!module.equals("support-service")) {
            command.add("--spring.profiles.active=outpatient-mvp");
            command.add("--spring.datasource.url=" + url(module.equals("report-service") ? "mediflow_report" : "mediflow_notification"));
        }
        var builder = new ProcessBuilder(command);
        var env = builder.environment();
        env.put("MEDIFLOW_REPORT_DB_URL", url("mediflow_report"));
        env.put("MEDIFLOW_NOTIFICATION_DB_URL", url("mediflow_notification"));
        env.put("MEDIFLOW_REPORT_DB_USER", "postgres"); env.put("MEDIFLOW_REPORT_DB_PASSWORD", "postgres");
        env.put("MEDIFLOW_NOTIFICATION_DB_USER", "postgres"); env.put("MEDIFLOW_NOTIFICATION_DB_PASSWORD", "postgres");
        env.put("MEDIFLOW_DB_USER", "postgres"); env.put("MEDIFLOW_DB_PASSWORD", "postgres");
        env.put("MEDIFLOW_RABBIT_HOST", "127.0.0.1"); env.put("MEDIFLOW_RABBIT_PORT", rabbitPort);
        env.put("MEDIFLOW_RABBIT_USER", "guest"); env.put("MEDIFLOW_RABBIT_PASSWORD", "guest");
        env.put("MEDIFLOW_JWT_SECRET", SupportSecurityTest.SECRET);
        Process process = builder.redirectErrorStream(true).redirectOutput(evidence.resolve(label + ".log").toFile()).start();
        children.add(process);
        modules.put(process, module);
        return process;
    }

    private void healthy(Process process, int port) {
        await().atMost(Duration.ofSeconds(100)).pollInterval(Duration.ofMillis(500))
                .ignoreExceptionsMatching(e -> e instanceof java.net.ConnectException || e instanceof java.net.http.HttpConnectTimeoutException)
                .untilAsserted(() -> {
                    if (!process.isAlive()) throw new IllegalStateException("Child exited; inspect rehearsal log");
                    assertThat(send(port, "GET", "/actuator/health", null, null).statusCode()).isEqualTo(200);
                });
    }

    private String write(int port, String phase) throws Exception {
        String body = JSON.writeValueAsString(Map.of("patientId", SupportSecurityTest.PATIENT,
                "title", "H6 clone " + phase, "content", "Isolated operator rehearsal " + UUID.randomUUID(), "channel", "IN_APP"));
        var response = send(port, "POST", "/api/v1/notifications/send", body, SupportSecurityTest.token("ADMIN", "access"));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return JSON.readTree(response.body()).path("data").path("notificationId").asText();
    }

    private void verify(int port, List<String> notices) throws Exception {
        for (String id : notices) {
            var response = send(port, "GET", "/api/v1/notifications/" + id, null, SupportSecurityTest.token("PATIENT", "access"));
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(response.body()).path("data").path("notificationId").asText()).isEqualTo(id);
            assertThat(send(port, "GET", "/api/v1/notifications/" + id, null,
                    SupportSecurityTest.token("PATIENT", "access", UUID.randomUUID())).statusCode()).isEqualTo(403);
        }
    }

    private void assertReport(int port) throws Exception {
        assertThat(send(port, "GET", "/api/v1/reports/daily?date=2026-10-17", null,
                SupportSecurityTest.token("MANAGER", "access")).statusCode()).isEqualTo(200);
    }

    private String snapshot(String database, boolean historyOnly) throws Exception {
        var digests = new ArrayList<String>();
        try (var connection = DriverManager.getConnection(url(database), "postgres", "postgres")) {
            connection.setReadOnly(true); connection.setAutoCommit(false);
            try (var query = connection.createStatement()) {
                query.setQueryTimeout(10);
                var tables = query.executeQuery("SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename");
                List<String> names = new ArrayList<>();
                while (tables.next()) names.add(tables.getString(1));
                for (String name : names) {
                    if (historyOnly && !name.equals("flyway_schema_history")) continue;
                    assertThat(name).matches("[a-z_0-9]+");
                    var rows = query.executeQuery("SELECT row_to_json(t)::text FROM \"" + name + "\" t ORDER BY row_to_json(t)::text");
                    while (rows.next()) digests.add(name + ":" + sha(rows.getString(1).getBytes(StandardCharsets.UTF_8)));
                    digests.add(name);
                }
            } finally { connection.rollback(); }
        }
        return sha(String.join("\n", digests).getBytes(StandardCharsets.UTF_8));
    }

    private String history(String database) throws Exception { return snapshot(database, true); }

    private void captureNotices(Map<String, String> hashes, List<String> ids) throws Exception {
        for (String id : ids) hashes.putIfAbsent(id, noticeHash(id));
    }

    private void preserveNotices(Map<String, String> hashes) throws Exception {
        for (var entry : hashes.entrySet()) assertThat(noticeHash(entry.getKey())).isEqualTo(entry.getValue());
    }

    private String noticeHash(String id) throws Exception {
        try (var connection = DriverManager.getConnection(url("mediflow_notification"), "postgres", "postgres");
                var query = connection.prepareStatement("SELECT row_to_json(n)::text FROM notification n WHERE notification_id=?")) {
            query.setObject(1, UUID.fromString(id));
            var result = query.executeQuery(); assertThat(result.next()).isTrue();
            String hash = sha(result.getString(1).getBytes(StandardCharsets.UTF_8));
            assertThat(result.next()).isFalse(); return hash;
        }
    }

    private void assertMarker(ConnectionFactory factory, String queue, byte[] bytes) throws Exception {
        try (var connection = factory.newConnection(); var channel = connection.createChannel()) {
            assertThat(channel.queueDeclarePassive(queue).getMessageCount()).isEqualTo(1);
            var delivery = channel.basicGet(queue, false); assertThat(delivery).isNotNull();
            assertThat(delivery.getBody()).isEqualTo(bytes);
            assertThat(delivery.getProps().getDeliveryMode()).isEqualTo(2);
            channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
        }
    }

    private HttpResponse<String> send(int port, String method, String path, String body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10));
        if (token != null) builder.header("Authorization", token);
        if (body != null) builder.header("Content-Type", "application/json");
        return HTTP.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String url(String database) { return "jdbc:postgresql://127.0.0.1:" + pgPort + "/" + database; }
    private static int port() throws Exception { try (var socket = new ServerSocket(0)) { return socket.getLocalPort(); } }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static String required(String name) {
        String value = System.getenv(name);
        assertThat(value).as("Run the operator clone script first; missing " + name).isNotBlank(); return value;
    }
    private static void stop(Process process) throws Exception {
        if (!process.isAlive()) return;
        process.destroy();
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly(); assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
