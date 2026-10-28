package com.mediflow.lab.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Both EXAM and LAB_TEST use actual Billing HTTP payment and dispatcher/Rabbit clearance.
 */
@Testcontainers
class PackagedOutpatientLabAcceptanceIT {
    // Retain child logs outside JUnit's temporary directory: Windows can briefly lock
    // redirected output after the child exits. Process termination is still asserted.
    private Path processLog(String name) throws java.io.IOException {
        Path logs = Path.of("target", "acceptance-logs", work.getFileName().toString());
        Files.createDirectories(logs);
        return logs.resolve(name + ".log");
    }

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
    private final Map<String, ProcessBuilder> launchers = new java.util.HashMap<>();

    @Test
    void persistedClinicalOrderCanBeStartedAndAttachedAfterActualPayment() throws Exception {
        Path coreJar = requiredJar("lab.acceptance.core.jar");
        Path gatewayJar = requiredJar("lab.acceptance.gateway.jar");
        Path eurekaJar = requiredJar("lab.acceptance.eureka.jar");
        Path clinicalJar = requiredJar("lab.acceptance.clinical.jar");
        Path labJar = requiredJar("lab.acceptance.lab.jar");
        Path billingJar = requiredJar("lab.acceptance.billing.jar");
        createDatabases();

        int eurekaPort = freePort();
        int corePort = freePort();
        int clinicalPort = freePort();
        int labPort = freePort();
        int gatewayPort = freePort();
        int billingPort = freePort();
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
                    Map.entry("SPRING_PROFILES_ACTIVE", "local"),
                    Map.entry("MEDIFLOW_DEMO_SEED_ENABLED", "false"),
                    Map.entry("MEDIFLOW_BOOTSTRAP_ADMIN_ENABLED", "true"),
                    Map.entry("MEDIFLOW_BOOTSTRAP_ADMIN_USERNAME", "acceptance.admin"),
                    Map.entry("MEDIFLOW_BOOTSTRAP_ADMIN_PASSWORD", PASSWORD),
                    Map.entry("MEDIFLOW_BOOTSTRAP_ADMIN_IDENTITY_NUMBER", "999999999901"),
                    Map.entry("MEDIFLOW_BOOTSTRAP_ADMIN_FULL_NAME", "Isolated Acceptance Admin"),
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
                    "--logging.level.com.mediflow.lab.infrastructure.client.LabBillingClearanceFeignClient=DEBUG",
                    "--spring.cloud.openfeign.client.config.labBillingExecutionClient.loggerLevel=BASIC",
                    "--mediflow.events.outbox-poll-interval=250"));
            awaitHealthy(labPort, "lab");
            awaitRegistered(eurekaPort, "LAB-SERVICE");

            Path catalog = work.resolve("billing-catalog.yml");
            Files.writeString(catalog, """
                    billing:
                      care-finance:
                        prices:
                          OUTPATIENT_EXAM:
                            description: Outpatient exam acceptance
                            unit-amount: 100000.00
                          LAB-CBC:
                            description: CBC acceptance
                            unit-amount: 150000.00
                    """);
            processes.add(start("billing", billingJar, billingPort, Map.ofEntries(
                    Map.entry("MEDIFLOW_JWT_SECRET", JWT_SECRET),
                    Map.entry("SPRING_DATASOURCE_URL", databaseUrl("mediflow_billing")),
                    Map.entry("SPRING_DATASOURCE_USERNAME", POSTGRES.getUsername()),
                    Map.entry("SPRING_DATASOURCE_PASSWORD", POSTGRES.getPassword()),
                    Map.entry("SPRING_RABBITMQ_HOST", RABBIT.getHost()),
                    Map.entry("SPRING_RABBITMQ_PORT", Integer.toString(RABBIT.getAmqpPort())),
                    Map.entry("SPRING_RABBITMQ_USERNAME", RABBIT.getAdminUsername()),
                    Map.entry("SPRING_RABBITMQ_PASSWORD", RABBIT.getAdminPassword())),
                    "--eureka.client.service-url.defaultZone=" + eurekaUrl,
                    "--spring.config.additional-location=" + catalog.toUri(),
                    "--mediflow.features.outpatient-mvp.enabled=true",
                    "--mediflow.billing.ledger.enabled=true",
                    "--mediflow.billing.clearance-lookup.enabled=true",
                    "--mediflow.billing.payment-request-discovery.enabled=true",
                    "--mediflow.billing.exam-charge-consumer.enabled=true",
                    "--mediflow.billing.lab-test-charge-consumer.enabled=true",
                    "--mediflow.billing.outpatient-publication.enabled=true",
                    "--mediflow.billing.outpatient-publication.purposes=EXAM,LAB_TEST",
                    "--mediflow.billing.outpatient-publication.acceptance-reference=V-CLOSE-03-isolated-EXAM-LAB"));
            awaitHealthy(billingPort, "billing");
            awaitRegistered(eurekaPort, "BILLING-SERVICE");

            processes.add(start("gateway", gatewayJar, gatewayPort, Map.of(
                    "MEDIFLOW_JWT_SECRET", JWT_SECRET),
                    "--eureka.client.service-url.defaultZone=" + eurekaUrl,
                    "--eureka.client.registry-fetch-interval-seconds=1"));
            awaitHealthy(gatewayPort, "gateway");

            String setupAdmin = login(gatewayUrl, "acceptance.admin");
            awaitGatewayCoreReady(gatewayUrl, setupAdmin);
            String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            UUID departmentId = UUID.fromString(data(post(gatewayUrl, "/api/v1/org/departments", setupAdmin,
                    "departmentName", "Acceptance Lab " + unique,
                    "abbreviation", "AL" + unique.substring(0, 4).toUpperCase(java.util.Locale.ROOT),
                    "departmentType", "CLINICAL", "location", "Acceptance site"))
                    .path("departmentId").asText());
            UUID doctorStaffId = createStaff(gatewayUrl, setupAdmin, departmentId, unique,
                    "DOCTOR", "doctor");
            UUID otherDoctorId = createStaff(gatewayUrl, setupAdmin, departmentId, unique,
                    "DOCTOR", "otherdoctor");
            UUID nurseStaffId = createStaff(gatewayUrl, setupAdmin, departmentId, unique,
                    "NURSE", "nurse");
            UUID labTechStaffId = createStaff(gatewayUrl, setupAdmin, departmentId, unique,
                    "TECHNICIAN", "labtech");
            createAccount(gatewayUrl, setupAdmin, "lab-doctor-" + unique, "DOCTOR", doctorStaffId);
            createAccount(gatewayUrl, setupAdmin, "lab-otherdoctor-" + unique, "DOCTOR", otherDoctorId);
            createAccount(gatewayUrl, setupAdmin, "lab-nurse-" + unique, "NURSE", nurseStaffId);
            createAccount(gatewayUrl, setupAdmin, "lab-tech-" + unique, "LAB_TECH", labTechStaffId);
            UUID cashierId = createStaff(gatewayUrl, setupAdmin, departmentId, unique, "CASHIER", "cashier");
            createAccount(gatewayUrl, setupAdmin, "lab-cashier-" + unique, "CASHIER", cashierId);
            String cashierToken = login(gatewayUrl, "lab-cashier-" + unique);
            UUID patientId = UUID.fromString(data(post(gatewayUrl, "/api/v1/patients", setupAdmin,
                    "hoTen", "Acceptance Lab Patient " + unique,
                    "ngaySinh", "1988-01-02", "gioiTinh", "M",
                    "soCmnd", digits(12), "diaChi", "Acceptance address",
                    "soDienThoai", digits(10), "email", "lab-patient-" + unique + "@example.test",
                    "bhytSo", null)).path("maBenhNhan").asText());

            String doctorToken = login(gatewayUrl, "lab-doctor-" + unique);
            String otherDoctorToken = login(gatewayUrl, "lab-otherdoctor-" + unique);
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
            armRecovery("clinical", "financial.clearance.granted");
            JsonNode examPayment = payActualSource(gatewayUrl, "EXAM", appointmentId, patientId, appointmentId, cashierToken, "100000.00");
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                    assertThat(data(get(gatewayUrl, "/api/v1/appointments/" + appointmentId, nurseToken))
                            .path("status").asText()).isEqualTo("READY_FOR_EXAM"));
            recoverAfterCommittedEffect("clinical", clinicalPort, "financial.clearance.granted");
            HttpResponse<String> examStarted = gatewayRequest(gatewayUrl, "PUT",
                    "/api/v1/appointments/" + appointmentId + "/start-exam", doctorToken, "{}");
            assertThat(examStarted.statusCode()).as(examStarted.body()).isEqualTo(200);
            JdbcTemplate clinicalDb = database("mediflow_clinical");
            JsonNode patientRecords = data(get(gatewayUrl,
                    "/api/v1/records/patient/" + patientId, doctorToken));
            assertThat(patientRecords.isArray()).isTrue();
            JsonNode record = java.util.stream.StreamSupport.stream(patientRecords.spliterator(), false)
                    .filter(candidate -> appointmentId.toString().equals(candidate.path("appointmentId").asText()))
                    .findFirst().orElseThrow();
            UUID recordId = UUID.fromString(record.path("recordId").asText());
            assertThat(count(clinicalDb, "SELECT count(*) FROM medical_record WHERE record_id=? AND appointment_id=? AND status='OPEN'",
                    recordId, appointmentId)).isOne();

            LocalDate requestedDate = LocalDate.now();
            String orderPath = "/api/v1/records/" + recordId + "/lab-orders";
            String orderBody = JSON.writeValueAsString(object("labType", "CBC",
                    "requestedDate", requestedDate.toString(), "priceCode", "LAB-CBC"));
            assertThat(gatewayRequest(gatewayUrl, "POST", orderPath, nurseToken, orderBody).statusCode())
                    .as("Gateway rejects a role outside ADMIN/DOCTOR").isEqualTo(403);
            assertThat(gatewayRequest(gatewayUrl, "POST", orderPath, otherDoctorToken, orderBody).statusCode())
                    .as("Clinical rejects a doctor who does not own the record").isEqualTo(403);
            assertThat(gatewayRequest(gatewayUrl, "PUT", orderPath, doctorToken, orderBody).statusCode())
                    .isEqualTo(403);
            assertThat(gatewayRequest(gatewayUrl, "POST", orderPath + "/extra", doctorToken, orderBody).statusCode())
                    .isEqualTo(403);
            assertThat(gatewayRequest(gatewayUrl, "GET", "/api/v1/lab-orders/" + UUID.randomUUID() + "/context",
                    doctorToken, null).statusCode()).as("Internal context has no public Gateway route").isEqualTo(404);
            assertThat(count(clinicalDb, "SELECT count(*) FROM clinical_lab_order WHERE record_id=?", recordId))
                    .isZero();
            HttpResponse<String> clinicalOrderResponse = gatewayRequest(gatewayUrl, "POST",
                    orderPath, doctorToken, orderBody);
            assertThat(clinicalOrderResponse.statusCode()).as(clinicalOrderResponse.body()).isEqualTo(201);
            JsonNode clinicalOrder = data(clinicalOrderResponse);
            UUID sourceOrderId = UUID.fromString(clinicalOrder.path("sourceOrderId").asText());
            assertThat(clinicalOrder.path("recordId").asText()).isEqualTo(recordId.toString());
            assertThat(clinicalOrder.path("patientId").asText()).isEqualTo(patientId.toString());
            assertThat(count(clinicalDb, "SELECT count(*) FROM clinical_lab_order WHERE source_order_id=? AND record_id=?",
                    sourceOrderId, recordId)).isOne();

            String labBase = gatewayUrl;
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

            armRecovery("lab", "financial.clearance.granted");
            JsonNode labPayment = payActualSource(gatewayUrl, "LAB_TEST", labTestId, patientId, appointmentId, cashierToken, "150000.00");
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                    assertThat(data(get(labBase, "/api/v1/lab/" + labTestId, labTechToken))
                            .path("status").asText()).isEqualTo("READY"));
            recoverAfterCommittedEffect("lab", labPort, "financial.clearance.granted");
            JsonNode clearance = replayActualLabClearance(labTestId);
            assertQuarantinedPaymentReceipts(examPayment, labPayment, patientId, appointmentId);
            assertThat(count(database("mediflow_lab"), "SELECT count(*) FROM lab_financial_clearance WHERE test_id=?",
                    labTestId)).isOne();

            Instant lookupTime = Instant.now();
            String lookupToken = Jwts.builder().subject("lab-service")
                    .claim(JwtClaims.ROLE, "SYSTEM").claim(JwtClaims.TYPE, JwtClaims.SERVICE_TOKEN_TYPE)
                    .issuedAt(Date.from(lookupTime)).expiration(Date.from(lookupTime.plusSeconds(60)))
                    .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8))).compact();
            HttpResponse<String> authority = request("http://localhost:" + billingPort, "GET",
                    "/api/v1/billing/financial-clearances/" + clearance.path("payload").path("clearanceId").asText()
                            + "/lookup", lookupToken, null);
            Files.writeString(Path.of("target", "paid-lab-authority.json"), authority.body());
            assertThat(authority.statusCode()).as(authority.body()).isEqualTo(200);
            assertThat(data(authority).path("eligible").asBoolean()).as(authority.body()).isTrue();

            HttpResponse<String> started = request(labBase, "PUT", "/api/v1/lab/" + labTestId + "/start",
                    labTechToken, "{}");
            assertThat(started.statusCode()).as(started.body()).isEqualTo(200);
            assertThat(data(started).path("status").asText()).isEqualTo("IN_PROGRESS");

            String performedDate = LocalDate.now().toString();
            armRecovery("clinical", "lab.result.created");
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
            recoverAfterCommittedEffect("clinical", clinicalPort, "lab.result.created");
            assertThat(count(clinicalDb,
                    "SELECT count(*) FROM attached_result WHERE record_id=? AND type='LAB' AND reference_id=?",
                    recordId, labTestId)).isOne();
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
            assertThat(clearance.path("payload").path("accountId")).isEqualTo(labPayment.path("accountId"));
            assertThat(clearance.path("payload").path("recordId").asText()).isEqualTo(recordId.toString());
            assertThat(clearance.path("payload").path("patientId").asText()).isEqualTo(patientId.toString());
            String recordPath = "/api/v1/records/" + recordId;
            String completion = JSON.writeValueAsString(object("disposition", "OUTPATIENT_FOLLOW_UP",
                    "dispositionNote", "Follow up after reviewed CBC"));
            assertThat(gatewayRequest(gatewayUrl, "PUT", recordPath + "/complete", nurseToken, completion)
                    .statusCode()).isEqualTo(403);
            assertThat(gatewayRequest(gatewayUrl, "PUT", recordPath + "/complete", otherDoctorToken, completion)
                    .statusCode()).isEqualTo(403);
            post(gatewayUrl, recordPath + "/diagnoses", doctorToken,
                    "diagnosisName", "Outpatient follow up", "description", "CBC reviewed", "icdCode", "Z09");
            JsonNode completedRecord = data(gatewayRequest(gatewayUrl, "PUT", recordPath + "/complete", doctorToken, completion));
            assertThat(completedRecord.path("status").asText()).isEqualTo("COMPLETED");
            assertThat(completedRecord.path("disposition").asText()).isEqualTo("OUTPATIENT_FOLLOW_UP");
            awaitBillingLifecycleSettled(clinicalDb, labDb, recordId, labTestId);
            assertThat(count(database("mediflow_billing"),
                    "SELECT count(*) FROM fee WHERE record_id=? AND fee_type IN ('EXAM','LAB')", recordId))
                    .as("V1 lifecycle facts must not create duplicate legacy fees after paid source charges")
                    .isZero();
            assertThat(data(get(gatewayUrl, "/api/v1/appointments/" + appointmentId, nurseToken))
                    .path("status").asText()).isEqualTo("COMPLETED");
            assertThat(gatewayRequest(gatewayUrl, "PUT", recordPath, doctorToken,
                    "{\"symptoms\":\"Attempt to edit completed record\"}").statusCode()).isEqualTo(422);
            assertThat(count(clinicalDb, "SELECT count(*) FROM clinical_outbox_event WHERE aggregate_id=? AND event_type='medicalrecord.completed'",
                    recordId)).isOne();
            Path evidence = Path.of("target", "paid-lab-evidence.json");
            Files.createDirectories(evidence.getParent());
            Files.writeString(evidence, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                    "appointmentId", appointmentId, "recordId", recordId, "labTestId", labTestId,
                    "sourceOrderId", sourceOrderId, "examPayment", examPayment, "labPayment", labPayment,
                    "clearance", clearance, "resultEvent", resultEvent, "completedRecord", completedRecord)));

        } finally {
            for (ServerSocket reservation : reservedPorts.values()) reservation.close();
            reservedPorts.clear();
            List<Long> processesNotStopped = new ArrayList<>();
            for (Process process : processesByName.values()) {
                if (!stop(process)) {
                    processesNotStopped.add(process.pid());
                }
            }
            assertThat(processesNotStopped)
                    .as("Service processes must exit before JUnit removes their redirected log files")
                    .isEmpty();
        }
    }

    private JsonNode payActualSource(String gateway, String purpose, UUID sourceId, UUID patientId,
                                     UUID episodeId, String cashierToken, String expectedAmount) throws Exception {
        String path = "/api/v1/billing/payment-requests/by-source?purpose=" + purpose + "&sourceId=" + sourceId
                + "&patientId=" + patientId + "&careEpisodeId=" + episodeId;
        JsonNode[] found = new JsonNode[1];
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(300)).untilAsserted(() -> {
            var response = gatewayRequest(gateway, "GET", path, cashierToken, null);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            found[0] = data(response);
        });
        JsonNode discovered = found[0];
        assertThat(discovered.path("sourceId").asText()).isEqualTo(sourceId.toString());
        assertThat(discovered.path("patientId").asText()).isEqualTo(patientId.toString());
        assertThat(discovered.path("careEpisodeId").asText()).isEqualTo(episodeId.toString());
        assertThat(discovered.path("remainingAmount").decimalValue()).isEqualByComparingTo(expectedAmount);
        String paymentPath = "/api/v1/billing/payment-requests/" + discovered.path("paymentRequestId").asText() + "/payments";
        String body = JSON.writeValueAsString(object("idempotencyKey", "paid-" + purpose + "-" + sourceId,
                "amount", discovered.path("remainingAmount").decimalValue(),
                "currency", discovered.path("currency").asText(), "paymentMethod", "CASH"));
        JsonNode payment = data(gatewayRequest(gateway, "POST", paymentPath, cashierToken, body));
        JsonNode replay = data(gatewayRequest(gateway, "POST", paymentPath, cashierToken, body));
        assertThat(replay).isEqualTo(payment);
        assertThat(payment.path("paymentRequestId")).isEqualTo(discovered.path("paymentRequestId"));
        JsonNode paid = data(get(gateway, path, cashierToken));
        assertThat(paid.path("status").asText()).isEqualTo("PAID");
        assertThat(paid.path("remainingAmount").decimalValue()).isZero();
        assertThat(paid.path("paidAmount").decimalValue()).isEqualByComparingTo(expectedAmount);
        return payment;
    }

    private JsonNode replayActualLabClearance(UUID labTestId) throws Exception {
        byte[] body = database("mediflow_lab").queryForObject("""
                SELECT raw_body FROM consumer_inbox_delivery
                WHERE routing_key='financial.clearance.granted'
                  AND convert_from(raw_body, 'UTF8')::jsonb->'payload'->'labTestIds' @> to_jsonb(?::text)
                """, byte[].class, labTestId.toString());
        JsonNode event = JSON.readTree(body);
        Path rawEvidence = Path.of("target", "paid-lab-clearance.json");
        Files.createDirectories(rawEvidence.getParent());
        Files.write(rawEvidence, body);
        UUID eventId = UUID.fromString(event.path("eventId").asText());
        UUID clearanceId = UUID.fromString(event.path("payload").path("clearanceId").asText());
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
            awaitLabQueuesSettled();
            return event;
        } finally {
            connection.destroy();
        }
    }

    private void awaitBillingLifecycleSettled(JdbcTemplate clinicalDb, JdbcTemplate labDb,
                                              UUID recordId, UUID testId) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(count(clinicalDb, "SELECT count(*) FROM clinical_outbox_event WHERE aggregate_id=? "
                    + "AND event_type='medicalrecord.created' AND published_at IS NOT NULL", recordId)).isOne();
            assertThat(count(labDb, "SELECT count(*) FROM lab_outbox_event WHERE aggregate_id=? "
                    + "AND event_type='lab.result.created' AND published_at IS NOT NULL", testId)).isOne();
            var queues = RABBIT.execInContainer("rabbitmqctl", "list_queues", "-p", "/", "name",
                    "messages_ready", "messages_unacknowledged", "messages");
            assertThat(queues.getExitCode()).as(queues.getStderr()).isZero();
            assertQueueCounts(queues.getStdout(), "billing.q", 0);
            assertQueueCounts(queues.getStdout(), "billing.dlq", 0);
            Files.writeString(Path.of("target", "billing-lifecycle-queues.txt"), queues.getStdout());
        });
    }

    private void awaitLabQueuesSettled() throws IOException, InterruptedException {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var queues = RABBIT.execInContainer("rabbitmqctl", "list_queues", "-p", "/", "name",
                    "messages_ready", "messages_unacknowledged", "messages");
            assertThat(queues.getExitCode()).as(queues.getStderr()).isZero();
            assertThat(queues.getStdout()).contains("messages_ready", "messages_unacknowledged", "messages");
            assertQueueCounts(queues.getStdout(), "lab.q", 0);
            // MVP deliberately quarantines payment.completed; only V1 clearance may unlock Lab.
            assertQueueCounts(queues.getStdout(), "lab.dlq", 2);
        });
    }

    private void assertQueueCounts(String queueList, String queueName, int ready) {
        String queueRow = queueList.lines().map(String::trim)
                .filter(line -> line.startsWith(queueName + " ") || line.startsWith(queueName + "\t"))
                .findFirst().orElseThrow(() -> new AssertionError("RabbitMQ did not report queue " + queueName));
        String[] columns = queueRow.split("\\s+");
        assertThat(columns).as(queueRow).hasSize(4);
        assertThat(columns[1]).as(queueName + " ready messages").isEqualTo(Integer.toString(ready));
        assertThat(columns[2]).as(queueName + " unacknowledged messages").isEqualTo("0");
        assertThat(columns[3]).as(queueName + " total messages").isEqualTo(Integer.toString(ready));
    }

    private void assertQuarantinedPaymentReceipts(JsonNode examPayment, JsonNode labPayment,
                                                 UUID patientId, UUID episodeId) throws Exception {
        Map<String, JsonNode> expected = Map.of(examPayment.path("transactionId").asText(), examPayment,
                labPayment.path("transactionId").asText(), labPayment);
        CachingConnectionFactory connection = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        connection.setUsername(RABBIT.getAdminUsername());
        connection.setPassword(RABBIT.getAdminPassword());
        try {
            new RabbitTemplate(connection).execute(channel -> {
                List<com.rabbitmq.client.GetResponse> received = new ArrayList<>();
                try {
                    java.util.Set<String> transactionIds = new java.util.HashSet<>();
                    for (int i = 0; i < 2; i++) {
                        var receipt = channel.basicGet("lab.dlq", false);
                        assertThat(receipt).as("Preserved payment.completed quarantine receipt").isNotNull();
                        received.add(receipt);
                        JsonNode event = JSON.readTree(receipt.getBody());
                        assertThat(event.path("eventType").asText()).isEqualTo("payment.completed");
                        assertThat(event.path("producer").asText()).isEqualTo("billing-service");
                        JsonNode payload = event.path("payload");
                        String transactionId = payload.path("transactionId").asText();
                        assertThat(transactionIds.add(transactionId)).isTrue();
                        assertThat(expected).containsKey(transactionId);
                        JsonNode payment = expected.get(transactionId);
                        assertThat(payload.path("paymentRequestId")).isEqualTo(payment.path("paymentRequestId"));
                        assertThat(payload.path("accountId")).isEqualTo(payment.path("accountId"));
                        assertThat(payload.path("totalAmount").decimalValue())
                                .isEqualByComparingTo(payment.path("amount").decimalValue());
                        assertThat(payload.path("patientId").asText()).isEqualTo(patientId.toString());
                        assertThat(payload.path("careEpisodeId").asText()).isEqualTo(episodeId.toString());
                        Files.write(Path.of("target", "quarantined-payment-" + transactionId + ".json"), receipt.getBody());
                    }
                    assertThat(transactionIds).containsExactlyInAnyOrderElementsOf(expected.keySet());
                    assertThat(channel.basicGet("lab.dlq", false)).isNull();
                } finally {
                    // Inspect without deleting financial evidence from the quarantine queue.
                    for (var receipt : received) channel.basicNack(receipt.getEnvelope().getDeliveryTag(), false, true);
                }
                return null;
            });
        } finally {
            connection.destroy();
        }
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
                "phoneNumber", digits(10), "email", label + "-" + unique + "@example.test",
                "identityNumber", digits(12)));
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
                .timeout(Duration.ofSeconds(30)).header("Accept", "application/json")
                .header(JwtClaims.HEADER_CORRELATION_ID, UUID.randomUUID().toString());
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
            statement.execute("CREATE DATABASE mediflow_billing");
        }
    }

    private String databaseUrl(String database) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getFirstMappedPort() + "/" + database;
    }

    private Process start(String name, Path jar, int port, Map<String, String> environment, String... arguments)
            throws IOException {
        ServerSocket reservation = reservedPorts.remove(port);
        if (reservation != null) reservation.close();
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        List<String> command = new ArrayList<>(List.of(java, "-Xmx256m"));
        if ("clinical".equals(name) || "lab".equals(name)) {
            Path fixtures = copyRecoveryLauncher(name, jar);
            command.addAll(List.of("-Dloader.main=com.mediflow.acceptance.fixture.ConsumerRecoveryProcess",
                    "-Dloader.path=" + fixtures, "-cp", jar.toString(),
                    "org.springframework.boot.loader.launch.PropertiesLauncher"));
        } else {
            command.addAll(List.of("-jar", jar.toString()));
        }
        command.addAll(List.of(
                "--management.endpoints.web.exposure.include=health,info",
                "--server.port=" + port, "--spring.main.banner-mode=off"));
        if (!"eureka".equals(name)) {
            command.addAll(List.of("--eureka.instance.hostname=127.0.0.1",
                    "--eureka.instance.ip-address=127.0.0.1", "--eureka.instance.prefer-ip-address=true"));
        }
        command.addAll(List.of(arguments));
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(processLog(name).toFile());
        builder.environment().putAll(environment);
        if ("clinical".equals(name) || "lab".equals(name)) {
            builder.environment().put("RECOVERY_BARRIER_ROUTE_FILE", work.resolve(name + ".route").toString());
            builder.environment().put("RECOVERY_HOLD_ACK_FILE", work.resolve(name + ".hold").toString());
            builder.environment().put("RECOVERY_DELIVERY_MARKER", work.resolve(name + ".body").toString());
        }
        launchers.put(name, builder);
        Process process = builder.start();
        processesByName.put(name, process);
        return process;
    }

    private Path copyRecoveryLauncher(String name, Path jar) throws IOException {
        Path packagePath = Path.of("com", "mediflow", "acceptance", "fixture");
        Path classes = jar.getParent().resolve("test-classes").resolve(packagePath);
        Path destination = work.resolve(name + "-launcher");
        Files.createDirectories(destination.resolve(packagePath));
        try (var files = Files.list(classes)) {
            for (Path file : files.filter(p -> p.getFileName().toString().startsWith("ConsumerRecoveryProcess")
                    && p.toString().endsWith(".class")).toList()) {
                Files.copy(file, destination.resolve(packagePath).resolve(file.getFileName()));
            }
        }
        assertThat(destination.resolve(packagePath).resolve("ConsumerRecoveryProcess.class")).isRegularFile();
        return destination;
    }

    private void armRecovery(String name, String route) throws IOException {
        Files.deleteIfExists(work.resolve(name + ".body"));
        Files.deleteIfExists(work.resolve(name + ".body.redelivered"));
        Files.writeString(work.resolve(name + ".hold"), "hold after commit before ACK");
        Files.writeString(work.resolve(name + ".route"), route);
    }

    private void recoverAfterCommittedEffect(String name, int port, String route) throws Exception {
        Path marker = work.resolve(name + ".body");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(marker).isRegularFile();
            assertThat(work.resolve(name + ".body.redelivered")).isRegularFile();
            assertThat(Files.readString(work.resolve(name + ".body.redelivered"))).isEqualTo("false");
        });
        byte[] original = Files.readAllBytes(marker);
        UUID eventId = UUID.fromString(JSON.readTree(original).path("eventId").asText());
        JdbcTemplate ownedDb = database("mediflow_" + name);
        assertThat(ownedDb.queryForObject("SELECT raw_body FROM consumer_inbox_delivery WHERE event_id=?",
                byte[].class, eventId)).isEqualTo(original);
        var queues = RABBIT.execInContainer("rabbitmqctl", "list_queues", "name", "messages_unacknowledged", "--formatter=json");
        assertThat(queues.getExitCode()).as(queues.getStderr()).isZero();
        boolean unacked = false;
        for (var queue : JSON.readTree(queues.getStdout())) {
            String queueName = "clinical".equals(name) && "financial.clearance.granted".equals(route)
                    ? "clinical.clearance.q" : name + ".q";
            if (queueName.equals(queue.path("name").asText())) {
                unacked = queue.path("messages_unacknowledged").asInt() > 0;
            }
        }
        assertThat(unacked).as("Actual committed %s delivery is still unacknowledged", route).isTrue();
        Process old = processesByName.get(name);
        old.destroyForcibly();
        assertThat(old.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        Files.delete(work.resolve(name + ".hold"));
        Files.delete(marker);
        Files.delete(work.resolve(name + ".body.redelivered"));
        Process restarted = launchers.get(name).start();
        processesByName.put(name, restarted);
        awaitHealthy(port, name);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(marker).isRegularFile();
            assertThat(work.resolve(name + ".body.redelivered")).isRegularFile();
            assertThat(Files.readAllBytes(marker)).isEqualTo(original);
            assertThat(Files.readString(work.resolve(name + ".body.redelivered"))).isEqualTo("true");
            assertThat(count(ownedDb, "SELECT count(*) FROM consumer_inbox_delivery WHERE event_id=?", eventId)).isOne();
        });
        String recoveredQueue = "clinical".equals(name) && "financial.clearance.granted".equals(route)
                ? "clinical.clearance.q" : name + ".q";
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            var settled = RABBIT.execInContainer("rabbitmqctl", "list_queues", "name", "messages_ready",
                    "messages_unacknowledged", "--formatter=json");
            assertThat(settled.getExitCode()).as(settled.getStderr()).isZero();
            JsonNode found = null;
            for (var queue : JSON.readTree(settled.getStdout())) {
                if (recoveredQueue.equals(queue.path("name").asText())) found = queue;
            }
            assertThat(found).as(recoveredQueue).isNotNull();
            assertThat(found.path("messages_ready").asInt()).isZero();
            assertThat(found.path("messages_unacknowledged").asInt()).isZero();
        });
        Files.delete(work.resolve(name + ".route"));
        Path evidence = Path.of("target", "integrated-recovery", name + "-" + route);
        Files.createDirectories(evidence);
        Files.write(evidence.resolve("original.json"), original);
        Files.write(evidence.resolve("redelivered.json"), Files.readAllBytes(marker));
        Files.writeString(evidence.resolve("result.json"), JSON.writeValueAsString(Map.of(
                "eventId", eventId, "oldPid", old.pid(), "newPid", restarted.pid(),
                "unacknowledgedBeforeKill", true, "redelivered", true, "inboxRows", 1,
                "readyAfterRecovery", 0, "unacknowledgedAfterRecovery", 0)));
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
            case "BILLING-SERVICE" -> "billing";
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
            return Files.readString(processLog(name));
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

    private final Map<Integer, ServerSocket> reservedPorts = new java.util.HashMap<>();

    private int freePort() throws IOException {
        // Keep the reservation until this JVM is ready to launch its child. Otherwise Windows
        // may reuse a future service port for an outgoing discovery/HTTP connection during startup.
        ServerSocket socket = new ServerSocket(0);
        reservedPorts.put(socket.getLocalPort(), socket);
        return socket.getLocalPort();
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
