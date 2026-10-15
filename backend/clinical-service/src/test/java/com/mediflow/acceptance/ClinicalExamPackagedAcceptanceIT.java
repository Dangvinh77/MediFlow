package com.mediflow.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.common.security.JwtClaims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Live HTTP acceptance for the Clinical-owned EXAM gate. The positive clearance case sends a
 * canonical Billing contract fixture with runtime Clinical target IDs; it is not a Billing
 * producer, invoice, or payment acceptance test.
 */
@Testcontainers
class ClinicalExamPackagedAcceptanceIT {
    private static final String JWT_SECRET = "clinical-packaged-acceptance-shared-secret-0123456789";
    private static final String PASSWORD = "Acceptance-pass-2026";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path work;

    @Test
    void packagedHttpCheckInFailsClosedUntilCanonicalClearanceThenStartsOneExam() throws Exception {
        Path clinicalJar = requiredJar("acceptance.clinical.jar");
        Path coreJar = requiredJar("acceptance.core.jar");
        Path gatewayJar = requiredJar("acceptance.gateway.jar");
        Path eurekaJar = requiredJar("acceptance.eureka.jar");
        createDatabases();

        int eurekaPort = freePort();
        int corePort = freePort();
        int clinicalPort = freePort();
        int gatewayPort = freePort();
        String eurekaUrl = "http://127.0.0.1:" + eurekaPort + "/eureka/";
        String organizationUrl = databaseUrl("mediflow_organization");
        String patientUrl = databaseUrl("mediflow_patient");
        String clinicalUrl = databaseUrl("mediflow_clinical");
        List<Process> processes = new ArrayList<>();
        try {
            processes.add(start("eureka", eurekaJar, eurekaPort, Map.of(),
                    "--eureka.client.register-with-eureka=false", "--eureka.client.fetch-registry=false",
                    "--eureka.server.enable-self-preservation=false",
                    "--eureka.server.response-cache-update-interval-ms=500"));
            awaitHealthy(eurekaPort, "eureka");

            processes.add(start("core", coreJar, corePort, Map.ofEntries(
                    Map.entry("MEDIFLOW_JWT_SECRET", JWT_SECRET),
                    Map.entry("MEDIFLOW_ORGANIZATION_DB_URL", organizationUrl),
                    Map.entry("MEDIFLOW_ORGANIZATION_DB_USER", POSTGRES.getUsername()),
                    Map.entry("MEDIFLOW_ORGANIZATION_DB_PASSWORD", POSTGRES.getPassword()),
                    Map.entry("MEDIFLOW_PATIENT_DB_URL", patientUrl),
                    Map.entry("MEDIFLOW_PATIENT_DB_USER", POSTGRES.getUsername()),
                    Map.entry("MEDIFLOW_PATIENT_DB_PASSWORD", POSTGRES.getPassword()),
                    Map.entry("MEDIFLOW_RABBIT_HOST", RABBIT.getHost()),
                    Map.entry("MEDIFLOW_RABBIT_PORT", Integer.toString(RABBIT.getAmqpPort())),
                    Map.entry("MEDIFLOW_RABBIT_USER", RABBIT.getAdminUsername()),
                    Map.entry("MEDIFLOW_RABBIT_PASSWORD", RABBIT.getAdminPassword()),
                    Map.entry("EUREKA_DEFAULT_ZONE", eurekaUrl))));
            awaitHealthy(corePort, "core");
            awaitRegistered(eurekaPort, "CORE-SERVICE", "core");

            processes.add(start("clinical", clinicalJar, clinicalPort, Map.ofEntries(
                    Map.entry("MEDIFLOW_JWT_SECRET", JWT_SECRET),
                    Map.entry("SPRING_DATASOURCE_URL", clinicalUrl),
                    Map.entry("SPRING_DATASOURCE_USERNAME", POSTGRES.getUsername()),
                    Map.entry("SPRING_DATASOURCE_PASSWORD", POSTGRES.getPassword()),
                    Map.entry("SPRING_RABBITMQ_HOST", RABBIT.getHost()),
                    Map.entry("SPRING_RABBITMQ_PORT", Integer.toString(RABBIT.getAmqpPort())),
                    Map.entry("SPRING_RABBITMQ_USERNAME", RABBIT.getAdminUsername()),
                    Map.entry("SPRING_RABBITMQ_PASSWORD", RABBIT.getAdminPassword())),
                    "--eureka.client.service-url.defaultZone=" + eurekaUrl,
                    "--mediflow.profile=outpatient-mvp",
                    "--mediflow.features.outpatient-mvp.enabled=true",
                    "--mediflow.features.care-finance-v2.enabled=true",
                    "--mediflow.clients.organization-service=core-service",
                    "--mediflow.clients.patient-service=core-service",
                    "--mediflow.clinical.outbox-poll-interval=250"));
            awaitHealthy(clinicalPort, "clinical");
            awaitRegistered(eurekaPort, "CLINICAL-SERVICE", "clinical");

            processes.add(start("gateway", gatewayJar, gatewayPort, Map.of(
                    "MEDIFLOW_JWT_SECRET", JWT_SECRET),
                    "--eureka.client.service-url.defaultZone=" + eurekaUrl,
                    "--eureka.client.registry-fetch-interval-seconds=1"));
            awaitHealthy(gatewayPort, "gateway");
            awaitRegistered(eurekaPort, "GATEWAY", "gateway");

            String gateway = "http://127.0.0.1:" + gatewayPort;
            String setupAdmin = setupAdminToken();
            String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            UUID departmentId = UUID.fromString(data(post(gateway, "/api/v1/org/departments", setupAdmin,
                    "departmentName", "Acceptance Clinical " + unique,
                    "abbreviation", ("AC" + unique.substring(0, 4)).toUpperCase(Locale.ROOT),
                    "departmentType", "CLINICAL", "location", "Acceptance site")).path("departmentId").asText());

            UUID doctorId = createStaff(gateway, setupAdmin, departmentId, unique, "DOCTOR", "assigned");
            UUID otherDoctorId = createStaff(gateway, setupAdmin, departmentId, unique, "DOCTOR", "other");
            UUID nurseId = createStaff(gateway, setupAdmin, departmentId, unique, "NURSE", "nurse");
            createAccount(gateway, setupAdmin, "doctor-" + unique, "DOCTOR", doctorId);
            createAccount(gateway, setupAdmin, "other-doctor-" + unique, "DOCTOR", otherDoctorId);
            createAccount(gateway, setupAdmin, "nurse-" + unique, "NURSE", nurseId);

            UUID patientId = UUID.fromString(data(post(gateway, "/api/v1/patients", setupAdmin,
                    "hoTen", "Acceptance Patient " + unique,
                    "ngaySinh", "1988-01-02", "gioiTinh", "M",
                    "soCmnd", digits(12), "diaChi", "Acceptance address",
                    "soDienThoai", digits(10), "email", "patient-" + unique + "@example.test",
                    "bhytSo", null)).path("maBenhNhan").asText());

            String nurseToken = login(gateway, "nurse-" + unique);
            String doctorToken = login(gateway, "doctor-" + unique);
            String otherDoctorToken = login(gateway, "other-doctor-" + unique);
            JsonNode createdAppointment = data(post(gateway, "/api/v1/appointments", nurseToken,
                    "patientId", patientId.toString(), "doctorId", doctorId.toString(),
                    "departmentId", departmentId.toString(), "appointmentDate", LocalDate.now().plusDays(1).toString(),
                    "appointmentTime", "09:00", "reason", "Packaged EXAM acceptance"));
            UUID appointmentId = UUID.fromString(createdAppointment.path("appointmentId").asText());
            JdbcTemplate clinicalDb = clinicalDb();

            HttpResponse<String> wrongRoleCheckIn = request(gateway, "PUT",
                    "/api/v1/appointments/" + appointmentId + "/check-in", doctorToken, "{}");
            assertThat(wrongRoleCheckIn.statusCode()).as(wrongRoleCheckIn.body()).isEqualTo(403);
            HttpResponse<String> unassignedDoctor = request(gateway, "PUT",
                    "/api/v1/appointments/" + appointmentId + "/start-exam", otherDoctorToken, "{}");
            assertThat(unassignedDoctor.statusCode()).as(unassignedDoctor.body()).isEqualTo(403);

            assertThat(data(get(gateway, "/api/v1/appointments/" + appointmentId, nurseToken))
                    .path("status").asText()).isEqualTo("PENDING");
            assertThat(count(clinicalDb, "SELECT count(*) FROM medical_record WHERE appointment_id = ?", appointmentId))
                    .isZero();

            assertConcurrentCheckIn(gateway, appointmentId, nurseToken);
            String checkInStatus = data(get(gateway, "/api/v1/appointments/" + appointmentId, nurseToken))
                    .path("status").asText();
            assertThat(checkInStatus).isEqualTo("AWAITING_PAYMENT");
            assertThat(count(clinicalDb, """
                    SELECT count(*) FROM appointment
                    WHERE appointment_id=? AND status='AWAITING_PAYMENT' AND care_contract_version=1
                      AND exam_price_code='OUTPATIENT_EXAM' AND checked_in_at IS NOT NULL
                    """, appointmentId)).isOne();
            assertThat(count(clinicalDb, """
                    SELECT count(*) FROM clinical_outbox_event
                    WHERE aggregate_id=? AND event_type='appointment.status.changed'
                      AND payload->'payload'->>'newStatus'='AWAITING_PAYMENT'
                      AND payload->'payload'->>'sourceType'='EXAM'
                    """, appointmentId)).isOne();
            HttpResponse<String> duplicateCheckIn = request(gateway, "PUT",
                    "/api/v1/appointments/" + appointmentId + "/check-in", nurseToken, "{}");
            assertClinicalFailure(duplicateCheckIn, 422, "CLINICAL_INVALID_STATUS_TRANSITION");
            assertThat(count(clinicalDb, """
                    SELECT count(*) FROM clinical_outbox_event
                    WHERE aggregate_id=? AND event_type='appointment.status.changed'
                      AND payload->'payload'->>'newStatus'='AWAITING_PAYMENT'
                    """, appointmentId)).isOne();
            HttpResponse<String> unpaidStart = request(gateway, "PUT",
                    "/api/v1/appointments/" + appointmentId + "/start-exam", doctorToken, "{}");
            assertClinicalFailure(unpaidStart, 422, "CLINICAL_EXAM_CLEARANCE_REQUIRED");
            assertThat(data(get(gateway, "/api/v1/appointments/" + appointmentId, nurseToken))
                    .path("status").asText()).isEqualTo("AWAITING_PAYMENT");
            assertThat(count(clinicalDb, "SELECT count(*) FROM medical_record WHERE appointment_id = ?", appointmentId))
                    .isZero();

            String fixtureLabel = publishCanonicalClearanceFixture(appointmentId, patientId);
            assertThat(fixtureLabel).isEqualTo("synthetic-contract-fixture");
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertThat(count(clinicalDb,
                        "SELECT count(*) FROM exam_clearance WHERE appointment_id=? AND patient_id=?",
                        appointmentId, patientId)).isOne();
                assertThat(data(get(gateway, "/api/v1/appointments/" + appointmentId, nurseToken))
                        .path("status").asText()).isEqualTo("READY_FOR_EXAM");
            });

            HttpResponse<String> started = request(gateway, "PUT",
                    "/api/v1/appointments/" + appointmentId + "/start-exam", doctorToken, "{}");
            assertThat(started.statusCode()).as(started.body()).isEqualTo(200);
            assertThat(data(started).path("status").asText()).isEqualTo("IN_EXAM");
            assertThat(count(clinicalDb,
                    "SELECT count(*) FROM medical_record WHERE appointment_id=? AND status='OPEN'", appointmentId))
                    .isOne();
            assertThat(count(clinicalDb, "SELECT count(*) FROM medical_record WHERE appointment_id=?", appointmentId))
                    .isOne();
            UUID recordId = clinicalDb.queryForObject(
                    "SELECT record_id FROM medical_record WHERE appointment_id=?", UUID.class, appointmentId);
            assertThat(recordId).isNotNull();
            assertThat(count(clinicalDb, """
                    SELECT count(*) FROM clinical_outbox_event
                    WHERE aggregate_id=? AND event_type='medicalrecord.created'
                      AND payload->'payload'->>'recordId'=?
                      AND payload->'payload'->>'sourceType'='EXAM'
                    """, recordId, recordId.toString())).isOne();
        } finally {
            AssertionError cleanupFailure = new AssertionError("Packaged service processes did not all stop");
            for (int i = processes.size() - 1; i >= 0; i--) {
                try {
                    stop(processes.get(i));
                } catch (RuntimeException | AssertionError failure) {
                    cleanupFailure.addSuppressed(failure);
                }
            }
            if (cleanupFailure.getSuppressed().length > 0) {
                throw cleanupFailure;
            }
        }
    }

    private void assertConcurrentCheckIn(String gateway, UUID appointmentId, String nurseToken) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<HttpResponse<String>> first = pool.submit(() -> concurrentCheckIn(gateway, appointmentId, nurseToken, ready, start));
            Future<HttpResponse<String>> second = pool.submit(() -> concurrentCheckIn(gateway, appointmentId, nurseToken, ready, start));
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<HttpResponse<String>> responses = List.of(first.get(), second.get());
            assertThat(responses.stream().filter(response -> response.statusCode() == 200)).hasSize(1);
            List<HttpResponse<String>> rejected = responses.stream()
                    .filter(response -> response.statusCode() != 200).toList();
            assertThat(rejected).hasSize(1);
            assertClinicalFailure(rejected.get(0), 422, "CLINICAL_INVALID_STATUS_TRANSITION");
        } finally {
            pool.shutdownNow();
        }
    }

    private HttpResponse<String> concurrentCheckIn(String gateway, UUID appointmentId, String token,
                                                   CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertThat(start.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        return request(gateway, "PUT", "/api/v1/appointments/" + appointmentId + "/check-in", token, "{}");
    }

    private String publishCanonicalClearanceFixture(UUID appointmentId, UUID patientId) throws Exception {
        JsonNode fixture;
        try (var input = getClass().getResourceAsStream("/contracts/financial.clearance.granted.v1.json")) {
            assertThat(input).isNotNull();
            fixture = JSON.readTree(input);
        }
        ObjectNode event = (ObjectNode) fixture;
        ObjectNode payload = (ObjectNode) event.path("payload");
        event.put("eventId", UUID.randomUUID().toString());
        event.put("occurredAt", Instant.now().toString());
        event.put("correlationId", UUID.randomUUID().toString());
        payload.put("clearanceId", UUID.randomUUID().toString());
        payload.put("invoiceId", UUID.randomUUID().toString());
        payload.put("accountId", UUID.randomUUID().toString());
        payload.put("appointmentId", appointmentId.toString());
        payload.put("careEpisodeId", appointmentId.toString());
        payload.put("patientId", patientId.toString());

        CachingConnectionFactory connection = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        connection.setUsername(RABBIT.getAdminUsername());
        connection.setPassword(RABBIT.getAdminPassword());
        connection.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.SIMPLE);
        try {
            RabbitAdmin admin = new RabbitAdmin(connection);
            await().atMost(Duration.ofSeconds(20))
                    .ignoreExceptionsMatching(ClinicalExamPackagedAcceptanceIT::isTransientConnectionFailure)
                    .untilAsserted(() ->
                    assertThat(admin.getQueueInfo("clinical.clearance.q")).isNotNull());
            RabbitTemplate rabbit = new RabbitTemplate(connection);
            MessageProperties properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setMessageId(event.path("eventId").asText());
            properties.setCorrelationId(event.path("correlationId").asText());
            byte[] body = JSON.writeValueAsBytes(event);
            rabbit.invoke(operations -> {
                operations.send("mediflow.events", "financial.clearance.granted", new Message(body, properties));
                operations.waitForConfirmsOrDie(10_000);
                return null;
            });
            return "synthetic-contract-fixture";
        } finally {
            connection.destroy();
        }
    }

    private UUID createStaff(String gateway, String token, UUID departmentId, String unique,
                             String jobTitle, String label) throws Exception {
        JsonNode staff = data(post(gateway, "/api/v1/org/staff", token,
                "fullName", "Acceptance " + label + " " + unique,
                "departmentId", departmentId.toString(), "jobTitle", jobTitle,
                "specialization", jobTitle.equals("DOCTOR") ? "General medicine" : null,
                "licenseNumber", jobTitle.equals("DOCTOR") ? "LIC-" + label + "-" + unique : null,
                "phoneNumber", digits(10), "email", label + "-" + unique + "@example.test"));
        return UUID.fromString(staff.path("staffId").asText());
    }

    private void createAccount(String gateway, String adminToken, String username,
                               String role, UUID staffId) throws Exception {
        post(gateway, "/api/v1/org/accounts", adminToken,
                "username", username, "password", PASSWORD, "staffId", staffId.toString(),
                "patientId", null, "role", role);
    }

    private String login(String gateway, String username) throws Exception {
        HttpResponse<String> response = request(gateway, "POST", "/api/v1/auth/login", null,
                JSON.writeValueAsString(Map.of("username", username, "password", PASSWORD)));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JSON.readTree(response.body()).path("accessToken").asText();
    }

    private String setupAdminToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim(JwtClaims.ROLE, "ADMIN")
                .claim(JwtClaims.TYPE, JwtClaims.ACCESS_TOKEN_TYPE)
                .claim(JwtClaims.CORRELATION_ID, UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private JsonNode post(String base, String path, String token, Object... fields) throws Exception {
        return successful(request(base, "POST", path, token, JSON.writeValueAsString(object(fields))));
    }

    private JsonNode get(String base, String path, String token) throws Exception {
        HttpResponse<String> response = request(base, "GET", path, token, null);
        return successful(response);
    }

    private JsonNode successful(HttpResponse<String> response) throws IOException {
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
        JsonNode root = JSON.readTree(response.body());
        assertThat(root.path("success").asBoolean()).as(response.body()).isTrue();
        return root;
    }

    private JsonNode data(HttpResponse<String> response) throws IOException {
        return successful(response).path("data");
    }

    private void assertClinicalFailure(HttpResponse<String> response, int status, String code) throws IOException {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(JSON.readTree(response.body()).path("error").path("code").asText())
                .as(response.body()).isEqualTo(code);
    }

    private JsonNode data(JsonNode response) {
        return response.path("data");
    }

    private HttpResponse<String> request(String base, String method, String path, String token, String body)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(30)).header("Accept", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        HttpRequest request = builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build();
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        while (true) {
            assertProcessRunning("gateway");
            try {
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (!gatewayRouteUnavailable(response) || System.nanoTime() >= deadline) {
                    return response;
                }
            } catch (ConnectException | HttpConnectTimeoutException exception) {
                if (System.nanoTime() >= deadline) {
                    throw exception;
                }
            }
            Thread.sleep(250);
        }
    }

    private static Map<String, Object> object(Object... fields) {
        var result = new java.util.LinkedHashMap<String, Object>();
        for (int i = 0; i < fields.length; i += 2) {
            result.put((String) fields[i], fields[i + 1]);
        }
        return result;
    }

    private JdbcTemplate clinicalDb() {
        var source = new DriverManagerDataSource(clinicalUrl("mediflow_clinical"), POSTGRES.getUsername(), POSTGRES.getPassword());
        return new JdbcTemplate(source);
    }

    private int count(JdbcTemplate jdbc, String query, Object... parameters) {
        Integer value = jdbc.queryForObject(query, Integer.class, parameters);
        return value == null ? 0 : value;
    }

    private void createDatabases() throws Exception {
        String adminUrl = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getFirstMappedPort() + "/postgres";
        try (var connection = java.sql.DriverManager.getConnection(adminUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE mediflow_organization");
            statement.execute("CREATE DATABASE mediflow_patient");
            statement.execute("CREATE DATABASE mediflow_clinical");
        }
    }

    private String databaseUrl(String database) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getFirstMappedPort() + "/" + database;
    }

    private String clinicalUrl(String database) {
        return databaseUrl(database);
    }

    private Process start(String name, Path jar, int port, Map<String, String> environment, String... arguments)
            throws IOException {
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        List<String> command = new ArrayList<>(List.of(java, "-Xmx256m", "-jar", jar.toString(),
                "--management.endpoints.web.exposure.include=health,info",
                "--server.port=" + port,
                "--eureka.instance.hostname=127.0.0.1",
                "--eureka.instance.prefer-ip-address=true",
                "--eureka.instance.ip-address=127.0.0.1",
                "--spring.main.banner-mode=off", "--eureka.client.initial-instance-info-replication-interval-seconds=1"));
        command.addAll(List.of(arguments));
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(work.resolve(name + ".log").toFile());
        builder.environment().putAll(environment);
        Process process = builder.start();
        processesByName.put(name, process);
        return process;
    }

    private void awaitHealthy(int port, String name) {
        URI health = URI.create("http://127.0.0.1:" + port + "/actuator/health");
        await().atMost(Duration.ofSeconds(120)).pollInterval(Duration.ofMillis(500))
                .ignoreExceptionsMatching(ClinicalExamPackagedAcceptanceIT::isTransientConnectionFailure)
                .untilAsserted(() -> {
            assertProcessRunning(name);
            HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(health).timeout(Duration.ofSeconds(2)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(response.body()).path("status").asText()).isEqualTo("UP");
        });
    }

    private void awaitRegistered(int port, String service, String processName) {
        URI app = URI.create("http://127.0.0.1:" + port + "/eureka/apps/" + service);
        await().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofSeconds(1))
                .ignoreExceptionsMatching(ClinicalExamPackagedAcceptanceIT::isTransientConnectionFailure)
                .untilAsserted(() -> {
            assertProcessRunning(processName);
            HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(app).header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        });
    }

    private final Map<String, Process> processesByName = new java.util.concurrent.ConcurrentHashMap<>();

    private Process processByName(String name) {
        return processesByName.get(name);
    }

    private void assertProcessRunning(String name) {
        Process process = processByName(name);
        if (process == null || !process.isAlive()) {
            throw new IllegalStateException(name + " process exited during startup or request:\n" + readLog(name));
        }
    }

    private String readLog(String name) {
        try {
            return Files.readString(work.resolve(name + ".log"));
        } catch (IOException ignored) {
            return "No startup log yet for " + name;
        }
    }

    private void stop(Process process) {
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    assertThat(process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS))
                            .as("packaged child JVM exits after forcible termination")
                            .isTrue();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                throw new IllegalStateException("Interrupted while stopping a packaged child JVM", interrupted);
            }
        }
        if (process != null) {
            assertThat(process.isAlive()).as("packaged child JVM has exited").isFalse();
        }
    }

    private int freePort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private String digits(int length) {
        StringBuilder value = new StringBuilder(length);
        while (value.length() < length) {
            value.append((int) (Math.random() * 10));
        }
        return value.toString();
    }

    private Path requiredJar(String property) {
        String value = System.getProperty(property);
        assertThat(value).as("Supply -D%s=/absolute/path/to/packaged.jar", property).isNotBlank();
        Path jar = Path.of(value).toAbsolutePath();
        assertThat(jar).as("Packaged dependency is required").isRegularFile();
        return jar;
    }

    private boolean gatewayRouteUnavailable(HttpResponse<String> response) throws IOException {
        if (response.statusCode() != 503) {
            return false;
        }
        return "GATEWAY_UPSTREAM_UNAVAILABLE".equals(
                JSON.readTree(response.body()).path("error").path("code").asText());
    }

    private static boolean isTransientConnectionFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException
                    || cause instanceof HttpConnectTimeoutException
                    || cause instanceof SocketTimeoutException
                    || cause instanceof org.springframework.amqp.AmqpConnectException) {
                return true;
            }
        }
        return false;
    }
}
