package com.mediflow.lab.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.common.security.JwtClaims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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

/**
 * Packaged HTTP/Rabbit acceptance for persisted Clinical Lab orders and Lab-owned effects.
 * The clearance is a contract fixture with live source IDs; this is not a Billing payment run.
 */
@Testcontainers
class PackagedOutpatientLabAcceptanceIT {
    private static final String JWT_SECRET = "lab-packaged-acceptance-shared-secret-0123456789";
    private static final String PASSWORD = "Acceptance-pass-2026";
    private static final String RESULT_CONCLUSION = "No abnormality observed";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path work;

    private final Map<String, Process> processesByName = new java.util.concurrent.ConcurrentHashMap<>();

    @Test
    void persistedClinicalOrderCanBeStartedAndAttachedAfterFixtureClearance() throws Exception {
        Path coreJar = requiredJar("lab.acceptance.core.jar");
        Path gatewayJar = requiredJar("lab.acceptance.gateway.jar");
        Path eurekaJar = requiredJar("lab.acceptance.eureka.jar");
        Path clinicalJar = requiredJar("lab.acceptance.clinical.jar");
        Path labJar = requiredJar("lab.acceptance.lab.jar");
        createDatabases();

        int eurekaPort = freePort();
        int corePort = freePort();
        int clinicalPort = freePort();
        int labPort = freePort();
        int gatewayPort = freePort();
        String eurekaUrl = "http://127.0.0.1:" + eurekaPort + "/eureka/";
        String coreUrl = "http://127.0.0.1:" + corePort;
        String clinicalUrl = "http://127.0.0.1:" + clinicalPort;
        String gatewayUrl = "http://127.0.0.1:" + gatewayPort;
        List<Process> processes = new ArrayList<>();
        try {
            processes.add(start("eureka", eurekaJar, eurekaPort, Map.of(),
                    "--eureka.client.register-with-eureka=false", "--eureka.client.fetch-registry=false",
                    "--eureka.server.enable-self-preservation=false",
                    "--eureka.server.response-cache-update-interval-ms=500"));
            awaitHealthy(eurekaPort, "eureka");

            processes.add(start("core", coreJar, corePort, Map.ofEntries(
                    Map.entry("MEDIFLOW_JWT_SECRET", JWT_SECRET),
                    Map.entry("MEDIFLOW_ORGANIZATION_DB_URL", databaseUrl("mediflow_organization")),
                    Map.entry("MEDIFLOW_ORGANIZATION_DB_USER", POSTGRES.getUsername()),
                    Map.entry("MEDIFLOW_ORGANIZATION_DB_PASSWORD", POSTGRES.getPassword()),
                    Map.entry("MEDIFLOW_PATIENT_DB_URL", databaseUrl("mediflow_patient")),
                    Map.entry("MEDIFLOW_PATIENT_DB_USER", POSTGRES.getUsername()),
                    Map.entry("MEDIFLOW_PATIENT_DB_PASSWORD", POSTGRES.getPassword()),
                    Map.entry("MEDIFLOW_RABBIT_HOST", RABBIT.getHost()),
                    Map.entry("MEDIFLOW_RABBIT_PORT", Integer.toString(RABBIT.getAmqpPort())),
                    Map.entry("MEDIFLOW_RABBIT_USER", RABBIT.getAdminUsername()),
                    Map.entry("MEDIFLOW_RABBIT_PASSWORD", RABBIT.getAdminPassword()),
                    Map.entry("EUREKA_DEFAULT_ZONE", eurekaUrl)),
                    "--eureka.client.service-url.defaultZone=" + eurekaUrl));
            awaitHealthy(corePort, "core");
            awaitRegistered(eurekaPort, "CORE-SERVICE");

            processes.add(start("clinical", clinicalJar, clinicalPort, Map.ofEntries(
                    Map.entry("MEDIFLOW_JWT_SECRET", JWT_SECRET),
                    Map.entry("SPRING_DATASOURCE_URL", databaseUrl("mediflow_clinical")),
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
            awaitRegistered(eurekaPort, "CLINICAL-SERVICE");

            processes.add(start("lab", labJar, labPort, Map.ofEntries(
                    Map.entry("MEDIFLOW_JWT_SECRET", JWT_SECRET),
                    Map.entry("SPRING_DATASOURCE_URL", databaseUrl("mediflow_lab")),
                    Map.entry("SPRING_DATASOURCE_USERNAME", POSTGRES.getUsername()),
                    Map.entry("SPRING_DATASOURCE_PASSWORD", POSTGRES.getPassword()),
                    Map.entry("SPRING_RABBITMQ_HOST", RABBIT.getHost()),
                    Map.entry("SPRING_RABBITMQ_PORT", Integer.toString(RABBIT.getAmqpPort())),
                    Map.entry("SPRING_RABBITMQ_USERNAME", RABBIT.getAdminUsername()),
                    Map.entry("SPRING_RABBITMQ_PASSWORD", RABBIT.getAdminPassword())),
                    "--spring.profiles.active=outpatient-mvp",
                    "--eureka.client.service-url.defaultZone=" + eurekaUrl,
                    "--mediflow.features.outpatient-mvp.enabled=true",
                    "--mediflow.features.care-finance-v2=true",
                    "--mediflow.lab.clinical-context.enabled=true",
                    "--mediflow.lab.clinical.base-url=" + clinicalUrl,
                    "--mediflow.events.outbox-poll-interval=250"));
            awaitHealthy(labPort, "lab");
            awaitRegistered(eurekaPort, "LAB-SERVICE");

            processes.add(start("gateway", gatewayJar, gatewayPort, Map.of(
                    "MEDIFLOW_JWT_SECRET", JWT_SECRET),
                    "--eureka.client.service-url.defaultZone=" + eurekaUrl,
                    "--eureka.client.registry-fetch-interval-seconds=1"));
            awaitHealthy(gatewayPort, "gateway");

            String setupAdmin = setupAdminToken();
            awaitGatewayCoreReady(gatewayUrl, setupAdmin);
            String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            UUID departmentId = UUID.fromString(data(post(gatewayUrl, "/api/v1/org/departments", setupAdmin,
                    "departmentName", "Acceptance Lab " + unique,
                    "abbreviation", "AL" + unique.substring(0, 4).toUpperCase(java.util.Locale.ROOT),
                    "departmentType", "CLINICAL", "location", "Acceptance site"))
                    .path("departmentId").asText());
            UUID doctorStaffId = createStaff(gatewayUrl, setupAdmin, departmentId, unique,
                    "DOCTOR", "doctor");
            UUID nurseStaffId = createStaff(gatewayUrl, setupAdmin, departmentId, unique,
                    "NURSE", "nurse");
            UUID labTechStaffId = createStaff(gatewayUrl, setupAdmin, departmentId, unique,
                    "TECHNICIAN", "labtech");
            createAccount(gatewayUrl, setupAdmin, "lab-doctor-" + unique, "DOCTOR", doctorStaffId);
            createAccount(gatewayUrl, setupAdmin, "lab-nurse-" + unique, "NURSE", nurseStaffId);
            createAccount(gatewayUrl, setupAdmin, "lab-tech-" + unique, "LAB_TECH", labTechStaffId);
            UUID patientId = UUID.fromString(data(post(gatewayUrl, "/api/v1/patients", setupAdmin,
                    "hoTen", "Acceptance Lab Patient " + unique,
                    "ngaySinh", "1988-01-02", "gioiTinh", "M",
                    "soCmnd", digits(12), "diaChi", "Acceptance address",
                    "soDienThoai", digits(10), "email", "lab-patient-" + unique + "@example.test",
                    "bhytSo", null)).path("maBenhNhan").asText());

            String doctorToken = login(gatewayUrl, "lab-doctor-" + unique);
            String nurseToken = login(gatewayUrl, "lab-nurse-" + unique);
            String labTechToken = login(gatewayUrl, "lab-tech-" + unique);
            UUID appointmentId = UUID.fromString(data(post(gatewayUrl, "/api/v1/appointments", nurseToken,
                    "patientId", patientId.toString(), "doctorId", doctorStaffId.toString(),
                    "departmentId", departmentId.toString(),
                    "appointmentDate", LocalDate.now().plusDays(1).toString(),
                    "appointmentTime", "09:00", "reason", "Packaged Lab acceptance"))
                    .path("appointmentId").asText());

            assertThat(gatewayRequest(gatewayUrl, "PUT", "/api/v1/appointments/" + appointmentId + "/check-in",
                    nurseToken, "{}").statusCode()).isEqualTo(200);
            HttpResponse<String> examStarted = gatewayRequest(gatewayUrl, "PUT",
                    "/api/v1/appointments/" + appointmentId + "/start-exam", doctorToken,
                    JSON.writeValueAsString(object(
                            "emergencyOverride", object(
                                    "overrideId", UUID.randomUUID().toString(),
                                    "approvedBy", doctorStaffId.toString(),
                                    "approverRole", "DOCTOR",
                                    "reason", "Approved test-only Clinical emergency override for Lab acceptance",
                                    "approvedAt", Instant.now().minusSeconds(1).toString()))));
            assertThat(examStarted.statusCode()).as(examStarted.body()).isEqualTo(200);
            JdbcTemplate clinicalDb = database("mediflow_clinical");
            JsonNode patientRecords = data(get("http://127.0.0.1:" + clinicalPort,
                    "/api/v1/records/patient/" + patientId, doctorToken));
            assertThat(patientRecords.isArray()).isTrue();
            JsonNode record = java.util.stream.StreamSupport.stream(patientRecords.spliterator(), false)
                    .filter(candidate -> appointmentId.toString().equals(candidate.path("appointmentId").asText()))
                    .findFirst().orElseThrow();
            UUID recordId = UUID.fromString(record.path("recordId").asText());
            assertThat(count(clinicalDb, "SELECT count(*) FROM medical_record WHERE record_id=? AND appointment_id=? AND status='OPEN'",
                    recordId, appointmentId)).isOne();

            LocalDate requestedDate = LocalDate.now();
            String directClinicalBase = "http://127.0.0.1:" + clinicalPort;
            HttpResponse<String> clinicalOrderResponse = request(directClinicalBase, "POST",
                    "/api/v1/records/" + recordId + "/lab-orders", doctorToken,
                    JSON.writeValueAsString(object("labType", "CBC", "requestedDate", requestedDate.toString(),
                            "priceCode", "LAB-CBC")));
            assertThat(clinicalOrderResponse.statusCode()).as(clinicalOrderResponse.body()).isEqualTo(201);
            JsonNode clinicalOrder = data(clinicalOrderResponse);
            UUID sourceOrderId = UUID.fromString(clinicalOrder.path("sourceOrderId").asText());
            assertThat(clinicalOrder.path("recordId").asText()).isEqualTo(recordId.toString());
            assertThat(clinicalOrder.path("patientId").asText()).isEqualTo(patientId.toString());
            assertThat(count(clinicalDb, "SELECT count(*) FROM clinical_lab_order WHERE source_order_id=? AND record_id=?",
                    sourceOrderId, recordId)).isOne();

            String labBase = "http://127.0.0.1:" + labPort;
            ObjectNode validRequest = (ObjectNode) JSON.valueToTree(object(
                    "recordId", recordId.toString(), "patientId", patientId.toString(),
                    "requestingDepartmentId", departmentId.toString(), "labType", "CBC",
                    "requestedDate", requestedDate.toString(), "sourceOrderId", sourceOrderId.toString(),
                    "careEpisodeType", "OUTPATIENT_VISIT", "careEpisodeId", appointmentId.toString(),
                    "priceCode", "LAB-CBC"));

            ObjectNode mismatch = validRequest.deepCopy();
            mismatch.put("patientId", UUID.randomUUID().toString());
            HttpResponse<String> mismatchResponse = request(labBase, "POST", "/api/v1/lab", doctorToken,
                    JSON.writeValueAsString(mismatch));
            assertThat(mismatchResponse.statusCode()).as(mismatchResponse.body()).isBetween(400, 499);
            assertThat(mismatchResponse.body()).contains("LAB_SOURCE_ORDER_MISMATCH");

            HttpResponse<String> createdResponse = request(labBase, "POST", "/api/v1/lab", doctorToken,
                    JSON.writeValueAsString(validRequest));
            assertThat(createdResponse.statusCode()).as(createdResponse.body()).isEqualTo(201);
            JsonNode created = data(createdResponse);
            UUID labTestId = UUID.fromString(created.path("testId").asText());
            assertThat(created.path("status").asText()).isEqualTo("AWAITING_PAYMENT");
            assertThat(created.path("sourceOrderId").asText()).isEqualTo(sourceOrderId.toString());

            HttpResponse<String> duplicateResponse = request(labBase, "POST", "/api/v1/lab", doctorToken,
                    JSON.writeValueAsString(validRequest));
            assertThat(duplicateResponse.statusCode()).as(duplicateResponse.body()).isEqualTo(409);
            assertThat(duplicateResponse.body()).contains("LAB_DUPLICATE_SOURCE_ORDER");

            HttpResponse<String> unpaidStart = request(labBase, "PUT", "/api/v1/lab/" + labTestId + "/start",
                    labTechToken, "{}");
            assertThat(unpaidStart.statusCode()).as(unpaidStart.body()).isBetween(400, 499);
            assertThat(unpaidStart.body()).contains("LAB_CLEARANCE_REQUIRED");
            assertThat(data(get(labBase, "/api/v1/lab/" + labTestId, labTechToken))
                    .path("status").asText()).isEqualTo("AWAITING_PAYMENT");

            JsonNode clearanceFixture = publishClearanceFixture(labTestId, recordId, patientId,
                    departmentId, appointmentId);
            assertThat(count(database("mediflow_lab"),
                    "SELECT count(*) FROM lab_test WHERE test_id=? AND status='READY' AND clearance_id=?",
                    labTestId, UUID.fromString(clearanceFixture.path("payload").path("clearanceId").asText()))).isOne();
            assertThat(count(database("mediflow_lab"), "SELECT count(*) FROM lab_financial_clearance WHERE test_id=?",
                    labTestId)).isOne();

            HttpResponse<String> started = request(labBase, "PUT", "/api/v1/lab/" + labTestId + "/start",
                    labTechToken, "{}");
            assertThat(started.statusCode()).as(started.body()).isEqualTo(200);
            assertThat(data(started).path("status").asText()).isEqualTo("IN_PROGRESS");

            String performedDate = LocalDate.now().toString();
            HttpResponse<String> resultResponse = request(labBase, "PUT",
                    "/api/v1/lab/" + labTestId + "/results", labTechToken,
                    JSON.writeValueAsString(object(
                            "results", List.of(object("indicator", "Hemoglobin", "value", "13.2",
                                    "unit", "g/dL", "referenceRange", "12.0-16.0")),
                            "conclusion", RESULT_CONCLUSION, "performedDate", performedDate)));
            assertThat(resultResponse.statusCode()).as(resultResponse.body()).isEqualTo(200);
            JsonNode completed = data(resultResponse);
            assertThat(completed.path("status").asText()).isEqualTo("COMPLETED");
            assertThat(completed.path("resultVersion").asInt()).isEqualTo(1);
            assertThat(completed.path("conclusion").asText()).isEqualTo(RESULT_CONCLUSION);

            JdbcTemplate labDb = database("mediflow_lab");
            await().atMost(Duration.ofSeconds(45)).untilAsserted(() -> {
                assertThat(count(clinicalDb, "SELECT count(*) FROM attached_result WHERE record_id=? AND type='LAB' AND reference_id=? AND summary=?",
                        recordId, labTestId, RESULT_CONCLUSION)).isOne();
                assertThat(count(clinicalDb,
                        "SELECT count(*) FROM attached_result WHERE record_id=? AND type='LAB' AND reference_id=?",
                        recordId, labTestId)).isOne();
                assertThat(count(labDb, "SELECT count(*) FROM lab_outbox_event WHERE aggregate_id=? AND event_type='lab.result.created'",
                        labTestId)).isOne();
            });
            JsonNode resultEvent = labOutboxPayload(labDb, labTestId);
            JsonNode resultPayload = resultEvent.path("payload");
            assertThat(resultPayload.path("labId").asText()).isEqualTo(labTestId.toString());
            assertThat(resultPayload.path("recordId").asText()).isEqualTo(recordId.toString());
            assertThat(resultPayload.path("patientId").asText()).isEqualTo(patientId.toString());
            assertThat(resultPayload.path("verifiedBy").asText()).isEqualTo(labTechStaffId.toString());
            assertThat(resultPayload.path("resultVersion").asInt()).isEqualTo(1);

            HttpResponse<String> terminalMutation = request(labBase, "PUT",
                    "/api/v1/lab/" + labTestId + "/results", labTechToken,
                    JSON.writeValueAsString(object(
                            "results", List.of(object("indicator", "Hemoglobin", "value", "99.9",
                                    "unit", "g/dL", "referenceRange", "12.0-16.0")),
                            "conclusion", "Changed after finalization", "performedDate", performedDate)));
            assertThat(terminalMutation.statusCode()).as(terminalMutation.body()).isEqualTo(409);
            assertThat(terminalMutation.body()).contains("LAB_RESULT_FINALIZED");
            JsonNode finalLabTest = data(get(labBase, "/api/v1/lab/" + labTestId, labTechToken));
            assertThat(finalLabTest.path("resultVersion").asInt()).isEqualTo(1);
            assertThat(finalLabTest.path("conclusion").asText()).isEqualTo(RESULT_CONCLUSION);
            assertThat(count(labDb, "SELECT count(*) FROM lab_result WHERE test_id=?", labTestId)).isOne();
        } finally {
            List<Long> processesNotStopped = new ArrayList<>();
            for (int i = processes.size() - 1; i >= 0; i--) {
                Process process = processes.get(i);
                if (!stop(process)) {
                    processesNotStopped.add(process.pid());
                }
            }
            assertThat(processesNotStopped)
                    .as("Service processes must exit before JUnit removes their redirected log files")
                    .isEmpty();
        }
    }

    private JsonNode publishClearanceFixture(UUID labTestId, UUID recordId, UUID patientId,
                                             UUID departmentId, UUID appointmentId) throws Exception {
        JsonNode fixture;
        try (var input = getClass().getResourceAsStream("/contracts/financial.clearance.granted.v1.json")) {
            assertThat(input).isNotNull();
            fixture = JSON.readTree(input);
        }
        ObjectNode event = (ObjectNode) fixture;
        ObjectNode payload = (ObjectNode) event.path("payload");
        UUID eventId = UUID.randomUUID();
        UUID clearanceId = UUID.randomUUID();
        event.put("eventId", eventId.toString());
        event.put("occurredAt", Instant.now().toString());
        event.put("correlationId", UUID.randomUUID().toString());
        payload.put("clearanceId", clearanceId.toString());
        payload.put("invoiceId", UUID.randomUUID().toString());
        payload.put("accountId", UUID.randomUUID().toString());
        payload.put("patientId", patientId.toString());
        payload.put("recordId", recordId.toString());
        payload.put("careEpisodeType", "OUTPATIENT_VISIT");
        payload.put("careEpisodeId", appointmentId.toString());
        payload.put("appointmentId", appointmentId.toString());
        ArrayNode labIds = (ArrayNode) payload.path("labTestIds");
        labIds.removeAll();
        labIds.add(labTestId.toString());

        CachingConnectionFactory connection = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        connection.setUsername(RABBIT.getAdminUsername());
        connection.setPassword(RABBIT.getAdminPassword());
        connection.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.SIMPLE);
        try {
            RabbitAdmin admin = new RabbitAdmin(connection);
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                assertThat(admin.getQueueInfo("lab.q")).isNotNull();
                assertThat(admin.getQueueInfo("clinical.q")).isNotNull();
            });
            byte[] body = JSON.writeValueAsBytes(event);
            MessageProperties properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setMessageId(eventId.toString());
            properties.setCorrelationId(event.path("correlationId").asText());
            RabbitTemplate rabbit = new RabbitTemplate(connection);
            rabbit.invoke(operations -> {
                operations.send("mediflow.events", "financial.clearance.granted", new Message(body, properties));
                operations.waitForConfirmsOrDie(10_000);
                operations.send("mediflow.events", "financial.clearance.granted", new Message(body, properties));
                operations.waitForConfirmsOrDie(10_000);
                return null;
            });
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                JdbcTemplate labDb = database("mediflow_lab");
                assertThat(count(labDb, "SELECT count(*) FROM lab_financial_clearance WHERE test_id=? AND clearance_id=?",
                        labTestId, clearanceId)).isOne();
                assertThat(count(labDb, "SELECT count(*) FROM consumer_inbox_delivery WHERE event_id=?",
                        eventId)).isOne();
                assertThat(count(labDb, "SELECT count(*) FROM lab_test WHERE test_id=? AND status='READY' AND clearance_id=?",
                        labTestId, clearanceId)).isOne();
            });
            awaitLabQueuesDrained();
            return event;
        } finally {
            connection.destroy();
        }
    }

    private void awaitLabQueuesDrained() throws IOException, InterruptedException {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var queues = RABBIT.execInContainer("rabbitmqctl", "list_queues", "-p", "/", "name",
                    "messages_ready", "messages_unacknowledged", "messages");
            assertThat(queues.getExitCode()).as(queues.getStderr()).isZero();
            assertThat(queues.getStdout()).contains("messages_ready", "messages_unacknowledged", "messages");
            assertQueueDrained(queues.getStdout(), "lab.q");
            assertQueueDrained(queues.getStdout(), "lab.dlq");
        });
    }

    private void assertQueueDrained(String queueList, String queueName) {
        String queueRow = queueList.lines().map(String::trim)
                .filter(line -> line.startsWith(queueName + " ") || line.startsWith(queueName + "\t"))
                .findFirst().orElseThrow(() -> new AssertionError("RabbitMQ did not report queue " + queueName));
        String[] columns = queueRow.split("\\s+");
        assertThat(columns).as(queueRow).hasSize(4);
        assertThat(columns[1]).as(queueName + " ready messages").isEqualTo("0");
        assertThat(columns[2]).as(queueName + " unacknowledged messages").isEqualTo("0");
        assertThat(columns[3]).as(queueName + " total messages").isEqualTo("0");
    }

    private JsonNode labOutboxPayload(JdbcTemplate labDb, UUID labTestId) throws Exception {
        String raw = labDb.queryForObject("SELECT payload::text FROM lab_outbox_event WHERE aggregate_id=? AND event_type='lab.result.created'",
                String.class, labTestId);
        assertThat(raw).isNotBlank();
        return JSON.readTree(raw);
    }

    private UUID createStaff(String gateway, String token, UUID departmentId, String unique,
                             String jobTitle, String label) throws Exception {
        JsonNode staff = data(post(gateway, "/api/v1/org/staff", token,
                "fullName", "Acceptance " + label + " " + unique,
                "departmentId", departmentId.toString(), "jobTitle", jobTitle,
                "specialization", "DOCTOR".equals(jobTitle) ? "General medicine" : null,
                "licenseNumber", "DOCTOR".equals(jobTitle) ? "LIC-" + label + "-" + unique : null,
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
        HttpResponse<String> response = gatewayRequest(gateway, "POST", "/api/v1/auth/login", null,
                JSON.writeValueAsString(Map.of("username", username, "password", PASSWORD)));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        String token = JSON.readTree(response.body()).path("accessToken").asText();
        assertThat(token).isNotBlank();
        return token;
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
        return successful(gatewayRequest(base, "POST", path, token, JSON.writeValueAsString(object(fields))));
    }

    private JsonNode get(String base, String path, String token) throws Exception {
        return successful(request(base, "GET", path, token, null));
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
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> gatewayRequest(String base, String method, String path, String token, String body)
            throws IOException, InterruptedException {
        HttpResponse<String> response = request(base, method, path, token, body);
        for (int attempt = 0; attempt < 120 && isMissingGatewayUpstream(response); attempt++) {
            Thread.sleep(250);
            response = request(base, method, path, token, body);
        }
        return response;
    }

    private boolean isMissingGatewayUpstream(HttpResponse<String> response) {
        return response.statusCode() == 503
                && (response.body().contains("GATEWAY_UPSTREAM_UNAVAILABLE")
                    || response.body().contains("No servers available"));
    }

    private void awaitGatewayCoreReady(String gateway, String adminToken) {
        await().ignoreExceptionsMatching(IOException.class::isInstance)
                .atMost(Duration.ofSeconds(45)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
                    HttpResponse<String> response = request(gateway, "GET",
                            "/api/v1/org/departments?activeOnly=true", adminToken, null);
                    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
                    assertThat(JSON.readTree(response.body()).path("success").asBoolean()).isTrue();
                });
    }

    private static Map<String, Object> object(Object... fields) {
        var result = new java.util.LinkedHashMap<String, Object>();
        for (int i = 0; i < fields.length; i += 2) {
            result.put((String) fields[i], fields[i + 1]);
        }
        return result;
    }

    private JdbcTemplate database(String database) {
        var source = new DriverManagerDataSource(databaseUrl(database), POSTGRES.getUsername(), POSTGRES.getPassword());
        return new JdbcTemplate(source);
    }

    private int count(JdbcTemplate jdbc, String query, Object... parameters) {
        Integer value = jdbc.queryForObject(query, Integer.class, parameters);
        return value == null ? 0 : value;
    }

    private void createDatabases() throws Exception {
        String adminUrl = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getFirstMappedPort() + "/postgres";
        try (var connection = DriverManager.getConnection(adminUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE mediflow_organization");
            statement.execute("CREATE DATABASE mediflow_patient");
            statement.execute("CREATE DATABASE mediflow_clinical");
            statement.execute("CREATE DATABASE mediflow_lab");
        }
    }

    private String databaseUrl(String database) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getFirstMappedPort() + "/" + database;
    }

    private Process start(String name, Path jar, int port, Map<String, String> environment, String... arguments)
            throws IOException {
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        List<String> command = new ArrayList<>(List.of(java, "-Xmx256m", "-jar", jar.toString(),
                "--management.endpoints.web.exposure.include=health,info",
                "--server.port=" + port, "--spring.main.banner-mode=off"));
        if (!"eureka".equals(name)) {
            command.addAll(List.of("--eureka.instance.hostname=127.0.0.1",
                    "--eureka.instance.ip-address=127.0.0.1", "--eureka.instance.prefer-ip-address=true"));
        }
        command.addAll(List.of(arguments));
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(work.resolve(name + ".log").toFile());
        builder.environment().putAll(environment);
        Process process = builder.start();
        processesByName.put(name, process);
        return process;
    }

    private void awaitHealthy(int port, String name) throws InterruptedException {
        URI health = URI.create("http://127.0.0.1:" + port + "/actuator/health");
        long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        IOException lastConnectionFailure = null;
        do {
            requireAlive(name);
            try {
                HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(health)
                        .timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200
                        && "UP".equals(JSON.readTree(response.body()).path("status").asText())) {
                    return;
                }
            } catch (IOException transientFailure) {
                lastConnectionFailure = transientFailure;
            }
            Thread.sleep(500);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Service did not become healthy: " + name + "\n" + readLog(name),
                lastConnectionFailure);
    }

    private void awaitRegistered(int port, String service) throws InterruptedException {
        URI app = URI.create("http://127.0.0.1:" + port + "/eureka/apps/" + service);
        String processName = switch (service) {
            case "CORE-SERVICE" -> "core";
            case "CLINICAL-SERVICE" -> "clinical";
            case "LAB-SERVICE" -> "lab";
            default -> throw new IllegalArgumentException("Unexpected service registration: " + service);
        };
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        IOException lastConnectionFailure = null;
        do {
            requireAlive(processName);
            try {
                HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(app)
                        .header("Accept", "application/json").timeout(Duration.ofSeconds(3)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (IOException transientFailure) {
                lastConnectionFailure = transientFailure;
            }
            Thread.sleep(500);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Service did not register with Eureka: " + service + "\n" + readLog(processName),
                lastConnectionFailure);
    }

    private void requireAlive(String name) {
        Process process = processesByName.get(name);
        if (process == null || !process.isAlive()) {
            throw new AssertionError("Service process exited before readiness: " + name + "\n" + readLog(name));
        }
    }

    private String readLog(String name) {
        try {
            return Files.readString(work.resolve(name + ".log"));
        } catch (IOException ignored) {
            return "No startup log yet for " + name;
        }
    }

    private boolean stop(Process process) {
        if (process == null) {
            return true;
        }
        if (process.isAlive()) {
            process.destroy();
        }
        try {
            if (process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                return true;
            }
            process.destroyForcibly();
            return process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            boolean stopped = false;
            try {
                stopped = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException secondInterruption) {
                interrupted.addSuppressed(secondInterruption);
            }
            Thread.currentThread().interrupt();
            return stopped;
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
}
