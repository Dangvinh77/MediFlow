package com.mediflow.report.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Report acceptance of bytes emitted by packaged Clinical after real public commands. Core and
 * Clinical stores are private: setup uses HTTP only. An existing audited emergency override drives
 * the source exam; this is not Billing payment, Gateway, Support, or historical coverage acceptance.
 */
@Testcontainers
@ActiveProfiles("outpatient-mvp")
@SpringBootTest(properties = {
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "mediflow.jwt.secret=report-clinical-source-acceptance-secret-at-least-32-bytes",
        "mediflow.features.care-finance-v2=true", "mediflow.report.outpatient-consumer.enabled=true",
        "mediflow.report.cash-refund-consumer.recovery-initial-delay-ms=3600000"})
class OutpatientClinicalSourceAcceptanceIT {
    private static final String SECRET = "report-clinical-source-acceptance-secret-at-least-32-bytes";
    private static final Network NETWORK = Network.newNetwork();
    @Container @ServiceConnection static final PostgreSQLContainer<?> REPORT_DB = database("report-db");
    @Container static final PostgreSQLContainer<?> ORGANIZATION_DB = database("organization-db");
    @Container static final PostgreSQLContainer<?> PATIENT_DB = database("patient-db");
    @Container static final PostgreSQLContainer<?> CLINICAL_DB = database("clinical-db");
    @Container @ServiceConnection static final RabbitMQContainer BROKER =
            new RabbitMQContainer("rabbitmq:3.13-alpine").withNetwork(NETWORK).withNetworkAliases("rabbit");
    @Container static final GenericContainer<?> CORE = service("core", 8089)
            .dependsOn(ORGANIZATION_DB, PATIENT_DB, BROKER)
            .withEnv("MEDIFLOW_ORGANIZATION_DB_URL", url("organization-db"))
            .withEnv("MEDIFLOW_ORGANIZATION_DB_USER", ORGANIZATION_DB.getUsername())
            .withEnv("MEDIFLOW_ORGANIZATION_DB_PASSWORD", ORGANIZATION_DB.getPassword())
            .withEnv("MEDIFLOW_PATIENT_DB_URL", url("patient-db"))
            .withEnv("MEDIFLOW_PATIENT_DB_USER", PATIENT_DB.getUsername())
            .withEnv("MEDIFLOW_PATIENT_DB_PASSWORD", PATIENT_DB.getPassword());
    @Container static final GenericContainer<?> CLINICAL = service("clinical", 8082)
            .dependsOn(CLINICAL_DB, CORE, BROKER)
            .withEnv("SPRING_DATASOURCE_URL", url("clinical-db"))
            .withEnv("MEDIFLOW_DB_USER", CLINICAL_DB.getUsername())
            .withEnv("MEDIFLOW_DB_PASSWORD", CLINICAL_DB.getPassword())
            .withEnv("SPRING_PROFILES_ACTIVE", "outpatient-mvp")
            .withEnv("MEDIFLOW_CARE_FINANCE_V2_ENABLED", "true")
            .withEnv("MEDIFLOW_CLINICAL_DURABLE_EVENTS_ENABLED", "true")
            .withEnv("MEDIFLOW_ORGANIZATION_SERVICE", "core-service")
            .withEnv("MEDIFLOW_PATIENT_SERVICE", "core-service")
            .withEnv("SPRING_APPLICATION_JSON", """
                    {"spring":{"cloud":{"discovery":{"client":{"simple":{"instances":{
                      "core-service":[{"uri":"http://core:8089"}]
                    }}}}}}}
                    """);

    @Autowired JdbcTemplate jdbc; // Report-owned store only.
    @Autowired RabbitListenerEndpointRegistry listeners;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @Test void actualOutpatientCompletionHasOneContributionAcrossListenerRestartReplayAndConflictingBytes() throws Exception {
        var department = request(CORE, 8089, "POST", "/api/v1/org/departments", null, Map.of(
                "departmentName", "Report source acceptance", "departmentType", "CLINICAL",
                "abbreviation", "R" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT)), 201);
        String departmentId = department.path("departmentId").asText();
        var doctor = request(CORE, 8089, "POST", "/api/v1/org/staff", null, Map.of(
                "fullName", "Report source doctor", "departmentId", departmentId, "jobTitle", "DOCTOR",
                "specialization", "Acceptance", "licenseNumber", "REPORT-SOURCE-TEST",
                "identityNumber", "999999999903"), 201);
        String doctorId = doctor.path("staffId").asText();
        var patient = request(CORE, 8089, "POST", "/api/v1/patients", null, Map.of(
                "hoTen", "Report source patient", "ngaySinh", "1990-01-01", "gioiTinh", "M",
                "soCmnd", UUID.randomUUID().toString().replace("-", "").substring(0, 12)), 201);
        String patientId = patient.path("maBenhNhan").asText();
        var appointment = request(CLINICAL, 8082, "POST", "/api/v1/appointments", doctorId, Map.of(
                "patientId", patientId, "doctorId", doctorId, "departmentId", departmentId,
                "appointmentDate", LocalDate.now().plusDays(1).toString(), "appointmentTime", "09:00",
                "reason", "Report source acceptance"), 201);
        String appointmentId = appointment.path("appointmentId").asText();
        String appointmentPath = "/api/v1/appointments/" + appointmentId;
        request(CLINICAL, 8082, "PUT", appointmentPath + "/check-in", doctorId, Map.of(), 200);
        request(CLINICAL, 8082, "PUT", appointmentPath + "/start-exam", doctorId, Map.of(
                "emergencyOverride", Map.of("overrideId", UUID.randomUUID(), "approvedBy", doctorId,
                        "approverRole", "ADMIN", "reason", "Isolated source acceptance, not paid-flow evidence",
                        "approvedAt", Instant.now().minusSeconds(1).toString())), 200);
        var records = request(CLINICAL, 8082, "GET", "/api/v1/records/patient/" + patientId, doctorId, null, 200);
        assertThat(records).hasSize(1);
        String recordId = records.get(0).path("recordId").asText();
        request(CLINICAL, 8082, "POST", "/api/v1/records/" + recordId + "/diagnoses", doctorId,
                Map.of("diagnosisName", "Source acceptance diagnosis", "icdCode", "Z00"), 201);

        var factory = new ConnectionFactory();
        factory.setHost(BROKER.getHost()); factory.setPort(BROKER.getAmqpPort());
        factory.setUsername(BROKER.getAdminUsername()); factory.setPassword(BROKER.getAdminPassword());
        try (var connection = factory.newConnection(); var channel = connection.createChannel()) {
            String probe = channel.queueDeclare("", false, true, true, Map.of()).getQueue();
            channel.queueBind(probe, "mediflow.events", "medicalrecord.completed");
            var completed = request(CLINICAL, 8082, "PUT", "/api/v1/records/" + recordId + "/complete", doctorId,
                    Map.of("disposition", "OUTPATIENT_FOLLOW_UP", "dispositionNote", "Isolated acceptance"), 200);
            assertThat(completed.path("status").asText()).isEqualTo("COMPLETED");
            GetResponse[] captured = new GetResponse[1];
            await().atMost(Duration.ofSeconds(30)).until(() -> (captured[0] = channel.basicGet(probe, true)) != null);
            byte[] original = captured[0].getBody();
            Path evidence = Path.of("target/acceptance-evidence");
            Files.createDirectories(evidence);
            Files.write(evidence.resolve("medicalrecord.completed.outpatient.actual.json"), original);
            JsonNode source = json.readTree(original);
            assertThat(source.path("producer").asText()).isEqualTo("clinical-service");
            assertThat(source.path("payload").path("recordId").asText()).isEqualTo(recordId);
            assertThat(source.path("payload").path("appointmentId").asText()).isEqualTo(appointmentId);
            assertThat(source.path("payload").path("disposition").asText()).isEqualTo("OUTPATIENT_FOLLOW_UP");
            assertThat(source.path("payload").path("admissionRequired").asBoolean()).isFalse();
            await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> assertThat(count("operational_event_journal")).isOne());
            var accepted = jdbc.queryForMap("SELECT * FROM operational_contribution WHERE metric_type='COMPLETED_VISITS'");
            assertThat(accepted.get("source_id").toString()).isEqualTo(recordId);
            assertThat(accepted.get("department_id").toString()).isEqualTo(departmentId);
            assertThat(((Number) accepted.get("numeric_value")).longValue()).isOne();

            channel.confirmSelect();
            var listener = listeners.getListenerContainer("reportOutpatientFacts");
            listener.stop();
            var properties = new AMQP.BasicProperties.Builder().contentType("application/json").deliveryMode(2).build();
            channel.basicPublish("mediflow.events", "medicalrecord.completed", true, properties, original);
            var replay = (ObjectNode) json.readTree(original);
            replay.put("eventId", UUID.randomUUID().toString());
            channel.basicPublish("mediflow.events", "medicalrecord.completed", true, properties, json.writeValueAsBytes(replay));
            channel.waitForConfirmsOrDie(5000);
            listener.start();
            await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> assertThat(count("operational_event_journal")).isEqualTo(2));

            replay.put("eventId", UUID.randomUUID().toString());
            ((ObjectNode) replay.path("payload")).put("departmentId", UUID.randomUUID().toString());
            byte[] conflict = json.writeValueAsBytes(replay);
            channel.basicPublish("mediflow.events", "medicalrecord.completed", true, properties, conflict);
            channel.waitForConfirmsOrDie(5000);
            GetResponse[] rejected = new GetResponse[1];
            await().atMost(Duration.ofSeconds(25)).until(() ->
                    (rejected[0] = channel.basicGet("report.outpatient-v1.dlq", true)) != null);
            assertThat(rejected[0].getBody()).isEqualTo(conflict);
            assertThat(count("operational_event_journal")).isEqualTo(2);
            assertThat(jdbc.queryForMap("SELECT * FROM operational_contribution WHERE metric_type='COMPLETED_VISITS'"))
                    .isEqualTo(accepted);
            assertThat(count("operational_contribution")).isOne();
            assertThat(count("report_outpatient_coverage")).isZero();
            assertThat(count("report_cash_receipt")).isZero();
            assertThat(count("financial_contribution")).isZero();
            System.out.println("REPORT_CLINICAL_SOURCE_ACCEPTANCE sourceSha256=" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(original)) + " contributions=1 acceptedDeliveries=2 conflictDlq=1 coverage=0");
        }
    }

    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }
    private JsonNode request(GenericContainer<?> app, int port, String method, String path,
            String staffId, Object body, int expected) throws Exception {
        var claims = Jwts.builder().subject(UUID.randomUUID().toString()).claim("role", "ADMIN").claim("type", "access");
        if (staffId != null) claims.claim("staffId", staffId);
        String token = claims.issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(180)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        var response = http.send(HttpRequest.newBuilder(URI.create("http://" + app.getHost() + ":" + app.getMappedPort(port) + path))
                .timeout(Duration.ofSeconds(12)).header("Authorization", "Bearer " + token)
                .header("X-Correlation-Id", UUID.randomUUID().toString()).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(method + " " + path + ": " + response.body()).isEqualTo(expected);
        return json.readTree(response.body()).path("data");
    }
    private static PostgreSQLContainer<?> database(String alias) {
        return new PostgreSQLContainer<>("postgres:16-alpine").withNetwork(NETWORK).withNetworkAliases(alias);
    }
    private static String url(String alias) { return "jdbc:postgresql://" + alias + ":5432/test"; }
    private static GenericContainer<?> service(String name, int port) {
        Path jar = Path.of("../" + name + "-service/target/" + name + "-service-0.0.1-SNAPSHOT.jar");
        if (!Files.isRegularFile(jar)) throw new IllegalStateException("Package current " + name + " first");
        var image = new ImageFromDockerfile().withFileFromPath("app.jar", jar).withDockerfileFromBuilder(builder ->
                builder.from("eclipse-temurin:21-jre-alpine").copy("app.jar", "/app/app.jar").user("10001")
                        .entryPoint("java", "-jar", "/app/app.jar").build());
        return new GenericContainer<>(image).withNetwork(NETWORK).withNetworkAliases(name).withExposedPorts(port)
                .withEnv("MEDIFLOW_JWT_SECRET", SECRET).withEnv("EUREKA_CLIENT_ENABLED", "false")
                .withEnv("MEDIFLOW_RABBIT_HOST", "rabbit").withEnv("MEDIFLOW_RABBIT_USER", BROKER.getAdminUsername())
                .withEnv("MEDIFLOW_RABBIT_PASSWORD", BROKER.getAdminPassword()).withEnv("JAVA_TOOL_OPTIONS", "-Xms64m -Xmx256m")
                .waitingFor(Wait.forHttp("/actuator/health").forPort(port).forStatusCode(200).withStartupTimeout(Duration.ofSeconds(180)));
    }
    @AfterAll static void close() {
        CLINICAL.stop(); CORE.stop(); CLINICAL_DB.stop(); PATIENT_DB.stop(); ORGANIZATION_DB.stop();
        REPORT_DB.stop(); BROKER.stop(); NETWORK.close();
    }
}
