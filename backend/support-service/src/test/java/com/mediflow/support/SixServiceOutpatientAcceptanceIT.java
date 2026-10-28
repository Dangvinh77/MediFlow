package com.mediflow.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Six packaged business apps with real Gateway/discovery, EXAM and prescription payments.
 * Business setup uses public APIs; SQL reconciles disposable test stores and injects an
 * isolated commit failure to verify rollback without changing business state directly.
 */
@Testcontainers
class SixServiceOutpatientAcceptanceIT {
    private Map<String, String> singleStore(String context, String eureka) {
        return Map.ofEntries(Map.entry("MEDIFLOW_JWT_SECRET", JWT_SECRET),
                Map.entry("SPRING_DATASOURCE_URL", databaseUrl("mediflow_" + context)),
                Map.entry("SPRING_DATASOURCE_USERNAME", POSTGRES.getUsername()),
                Map.entry("SPRING_DATASOURCE_PASSWORD", POSTGRES.getPassword()),
                Map.entry("MEDIFLOW_RABBIT_HOST", RABBIT.getHost()),
                Map.entry("MEDIFLOW_RABBIT_PORT", RABBIT.getAmqpPort().toString()),
                Map.entry("MEDIFLOW_RABBIT_USER", RABBIT.getAdminUsername()),
                Map.entry("MEDIFLOW_RABBIT_PASSWORD", RABBIT.getAdminPassword()),
                Map.entry("SPRING_RABBITMQ_HOST", RABBIT.getHost()),
                Map.entry("SPRING_RABBITMQ_PORT", RABBIT.getAmqpPort().toString()),
                Map.entry("SPRING_RABBITMQ_USERNAME", RABBIT.getAdminUsername()),
                Map.entry("SPRING_RABBITMQ_PASSWORD", RABBIT.getAdminPassword()),
                Map.entry("EUREKA_DEFAULT_ZONE", eureka));
    }

    private Map<String, String> supportEnvironment(String eureka) {
        var env = new java.util.HashMap<>(singleStore("report", eureka));
        if (reportCalendarAcceptance()) {
            env.put("MEDIFLOW_FE_REPORT_TIME_FILE", work.resolve("report-test-clock.txt").toAbsolutePath().toString());
            env.put("MEDIFLOW_REPORT_OUTPATIENT_COVERAGE_ACCEPTANCE_ENABLED", "true");
        }
        env.put("MEDIFLOW_NOTIFICATION_REFUND_CONSUMER_ENABLED", Boolean.toString(refundPublicationAcceptance()));
        for (String context : List.of("REPORT", "NOTIFICATION")) {
            env.put("MEDIFLOW_" + context + "_DB_URL", databaseUrl("mediflow_" + context.toLowerCase(Locale.ROOT)));
            env.put("MEDIFLOW_" + context + "_DB_USER", POSTGRES.getUsername());
            env.put("MEDIFLOW_" + context + "_DB_PASSWORD", POSTGRES.getPassword());
        }
        return env;
    }

    private JdbcTemplate store(String name) {
        return new JdbcTemplate(new DriverManagerDataSource(databaseUrl("mediflow_" + name),
                POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    private record PatientTokens(String own, String other, String manager, String refresh) { }

    private void exercisePrescription(String gateway, String admin, String doctor, String cashier, String nurse,
            PatientTokens patientTokens,
            UUID patient, UUID prescriber, UUID department, UUID episode, UUID record, int supportPort, int pharmacyPort, int billingPort,
            String eureka, List<Process> processes) throws Exception {
        JsonNode drug = data(post(gateway, "/api/v1/pharmacy/drugs", admin,
                "drugName", "Six deployment acceptance drug", "unit", "tablet", "price", 10000,
                "stockQuantity", 20, "expiryDate", LocalDate.now().plusYears(1).toString(), "lowStockThreshold", 0));
        String drugId = drug.path("drugId").asText();
        String body = JSON.writeValueAsString(object("recordId", record, "patientId", patient, "doctorId", prescriber,
                "departmentId", department, "prescribedDate", LocalDate.now().toString(), "lines",
                List.of(Map.of("drugId", drugId, "quantity", 3, "dosage", "Acceptance only")),
                "careContractVersion", 1, "careContext", "OUTPATIENT", "careEpisodeType", "OUTPATIENT_VISIT",
                "careEpisodeId", episode, "priceCode", "DRUG"));
        String key = UUID.randomUUID().toString();
        assertThat(createRx(gateway, nurse, key, body).statusCode()).isEqualTo(403);
        JsonNode created = data(createRx(gateway, doctor, key, body));
        UUID rx = UUID.fromString(created.path("prescriptionId").asText());
        assertThat(data(createRx(gateway, doctor, key, body)).path("prescriptionId").asText()).isEqualTo(rx.toString());
        String dispensePath = "/api/v1/pharmacy/prescriptions/" + rx + "/dispense";
        assertThat(request(gateway, "PUT", dispensePath, doctor, null).statusCode()).isEqualTo(403);
        assertThat(request(gateway, "PUT", dispensePath, admin, null).statusCode()).isEqualTo(422);

        String discovery = "/api/v1/billing/payment-requests/by-source?purpose=PRESCRIPTION&sourceId=" + rx
                + "&patientId=" + patient + "&careEpisodeId=" + episode;
        JsonNode[] found = new JsonNode[1];
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> found[0] = data(request(gateway, "GET", discovery, cashier, null)));
        JsonNode paymentRequest = found[0];
        assertThat(paymentRequest.path("remainingAmount").decimalValue()).isEqualByComparingTo("30000.00");
        String paymentPath = "/api/v1/billing/payment-requests/" + paymentRequest.path("paymentRequestId").asText() + "/payments";
        String paymentBody = JSON.writeValueAsString(Map.of("idempotencyKey", "paid-rx-" + rx, "amount", 30000,
                "currency", "VND", "paymentMethod", "CASH"));
        // The real issuer pays while Pharmacy is stopped; its durable grant must survive that outage.
        stop(processByName("pharmacy"));
        JsonNode payment = data(request(gateway, "POST", paymentPath, cashier, paymentBody));
        assertThat(data(request(gateway, "POST", paymentPath, cashier, paymentBody)).path("transactionId"))
                .isEqualTo(payment.path("transactionId"));
        processes.add(startPharmacy(pharmacyPort, billingPort, eureka));
        awaitHealthy(pharmacyPort, "pharmacy");
        awaitRegistered(URI.create(eureka).getPort(), "PHARMACY-SERVICE", "pharmacy");
        await().atMost(Duration.ofSeconds(45)).untilAsserted(() -> assertThat(count(store("pharmacy"),
                "SELECT count(*) FROM prescription_clearance WHERE prescription_id=?", rx)).isOne());
        assertThat(store("pharmacy").queryForObject("SELECT stock_quantity FROM drug WHERE drug_id=?", Integer.class,
                UUID.fromString(drugId))).isEqualTo(20);
        JsonNode dispensed = data(request(gateway, "PUT", dispensePath, admin, null));
        assertThat(dispensed.path("status").asText()).isEqualTo("DISPENSED");
        assertThat(data(request(gateway, "PUT", dispensePath, admin, null)).path("dispenseId"))
                .isEqualTo(dispensed.path("dispenseId"));
        awaitPrescriptionEffects(rx, record, UUID.fromString(drugId));
        assertThat(count(store("billing"), "SELECT count(*) FROM charge WHERE source_type='PRESCRIPTION' AND source_id=?", rx)).isOne();
        assertThat(count(store("billing"), "SELECT count(*) FROM payment_transaction WHERE payment_request_id=? AND transaction_type='PAYMENT' AND status='COMPLETED'",
                UUID.fromString(paymentRequest.path("paymentRequestId").asText()))).isOne();
        assertThat(count(store("billing"), "SELECT count(*) FROM financial_clearance WHERE prescription_id=? AND revoked_at IS NULL", rx)).isOne();
        assertThat(count(store("billing"), "SELECT count(*) FROM payment_transaction WHERE transaction_type='REFUND'")).isZero();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(count(store("report"), "SELECT count(*) FROM report_cash_receipt")).isEqualTo(2);
            assertThat(store("report").queryForObject("SELECT sum(amount) FROM report_cash_receipt", java.math.BigDecimal.class))
                    .isEqualByComparingTo("130000.00");
            assertThat(count(store("report"), "SELECT count(*) FROM report_cash_refund")).isZero();
        });
        String filledBytes = store("pharmacy").queryForObject(
                "SELECT payload FROM pharmacy_event_outbox WHERE aggregate_id=? AND routing_key='prescription.filled'", String.class, rx);
        String filledHash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(filledBytes.getBytes(StandardCharsets.UTF_8)));
        Files.writeString(work.resolve("prescription-filled-original.json"), filledBytes);
        Files.writeString(work.resolve("prescription-filled-sha256.txt"), filledHash);
        UUID notice = store("notification").queryForObject("SELECT notification_id FROM notification WHERE source_id=? AND template_key='OUTPATIENT_PRESCRIPTION_FILLED'", UUID.class, rx);
        String patientToken = patientTokens.own();
        String noticePath = "/api/v1/notifications/" + notice;
        assertThat(request(gateway, "GET", noticePath, patientToken, null).statusCode()).isEqualTo(200);
        assertThat(request(gateway, "GET", noticePath, patientTokens.other(), null).statusCode()).isEqualTo(403);
        assertThat(request(gateway, "GET", noticePath, patientTokens.manager(), null).statusCode()).isEqualTo(403);
        assertThat(request(gateway, "GET", noticePath, patientTokens.refresh(), null).statusCode()).isEqualTo(401);
        assertThat(request(gateway, "GET", "/api/v1/pharmacy/drugs/" + drugId + "/fake/nested", admin, null).statusCode()).isEqualTo(403);
        JsonNode registry = JSON.readTree(HTTP.send(HttpRequest.newBuilder(URI.create(eureka + "apps"))
                .header("Accept", "application/json").GET().build(), HttpResponse.BodyHandlers.ofString()).body());
        var applications = registry.path("applications").path("application");
        var names = new ArrayList<String>(); applications.forEach(a -> names.add(a.path("name").asText()));
        assertThat(names).contains("CORE-SERVICE", "CLINICAL-SERVICE", "LAB-SERVICE", "PHARMACY-SERVICE", "BILLING-SERVICE", "SUPPORT-SERVICE")
                .doesNotContain("REPORT-SERVICE", "NOTIFICATION-SERVICE", "INPATIENT-SERVICE", "SURGERY-SERVICE");
        assertThat(names).hasSize(7).contains("GATEWAY"); // Count apps, not their nested instance names.

        // Rehearse rollback against CURRENT stores. Stop the host before either legacy writer starts.
        var heldBefore = store("notification").queryForList("SELECT * FROM notification_event_outbox ORDER BY event_id");
        var reportBefore = store("report").queryForList("SELECT * FROM operational_event_journal ORDER BY event_id");
        stop(processByName("support"));
        int reportPort = freePort(), notificationPort = freePort();
        UUID rollbackWrite;
        processes.add(start("legacy-report", requiredJar("report"), reportPort, singleStore("report", eureka),
                "--eureka.client.enabled=false", "--spring.profiles.active=outpatient-mvp",
                "--mediflow.features.care-finance-v2=true", "--mediflow.report.outpatient-consumer.enabled=true"));
        processes.add(start("legacy-notification", requiredJar("notification"), notificationPort, singleStore("notification", eureka),
                "--eureka.client.enabled=false", "--spring.profiles.active=outpatient-mvp",
                "--mediflow.features.care-finance-v2=true", "--mediflow.notification.care-v1.enabled=true",
                "--mediflow.notification.outpatient-prescription-consumer.enabled=true"));
        try {
            awaitHealthy(reportPort, "legacy-report"); awaitHealthy(notificationPort, "legacy-notification");
            assertThat(request("http://127.0.0.1:" + notificationPort, "GET", noticePath, patientToken, null).statusCode()).isEqualTo(200);
            assertThat(request("http://127.0.0.1:" + reportPort, "GET", "/api/v1/reports/daily?date=" + LocalDate.now(), admin, null).statusCode()).isEqualTo(200);
            awaitPrescriptionEffects(rx, record, UUID.fromString(drugId));
            JsonNode accepted = data(post("http://127.0.0.1:" + notificationPort, "/api/v1/notifications/send", admin,
                    "patientId", patient, "title", "Accepted during rollback", "content", "Isolated acceptance", "channel", "IN_APP"));
            rollbackWrite = UUID.fromString(accepted.path("notificationId").asText());
        } finally { stop(processByName("legacy-notification")); stop(processByName("legacy-report")); }
        processes.add(start("support", requiredJar("support"), supportPort, supportEnvironment(eureka),
                "--mediflow.features.care-finance-v2=true", "--mediflow.report.outpatient-consumer.enabled=true",
                "--mediflow.report.outpatient-read.enabled=true", "--mediflow.notification.care-v1.enabled=true",
                "--mediflow.notification.outpatient-prescription-consumer.enabled=true"));
        awaitHealthy(supportPort, "support");
        assertThat(request("http://127.0.0.1:" + supportPort, "GET", noticePath, patientToken, null).statusCode()).isEqualTo(200);
        assertThat(request("http://127.0.0.1:" + supportPort, "GET", "/api/v1/notifications/" + rollbackWrite, patientToken, null).statusCode()).isEqualTo(200);
        awaitPrescriptionEffects(rx, record, UUID.fromString(drugId));
        assertThat(store("notification").queryForList("SELECT * FROM notification_event_outbox ORDER BY event_id")).containsAll(heldBefore);
        assertThat(store("report").queryForList("SELECT * FROM operational_event_journal ORDER BY event_id")).isEqualTo(reportBefore);
        assertThat(count(store("notification"), "SELECT count(*) FROM notification_event_outbox WHERE publication_enabled")).isZero();
        Files.writeString(work.resolve("paid-prescription-evidence.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(
                Map.of("prescriptionId", rx, "patientId", patient, "recordId", record, "episodeId", episode,
                        "drugId", drugId, "payment", payment, "noticeId", notice, "registeredApplications", names,
                        "supportRollbackOnCurrentStores", true)));
    }

    private void awaitPrescriptionEffects(UUID rx, UUID record, UUID drug) {
        await().atMost(Duration.ofSeconds(45)).untilAsserted(() -> {
            assertThat(store("pharmacy").queryForObject("SELECT stock_quantity FROM drug WHERE drug_id=?", Integer.class, drug)).isEqualTo(17);
            assertThat(count(store("clinical"), "SELECT count(*) FROM attached_result WHERE record_id=? AND type='PRESCRIPTION' AND reference_id=?", record, rx)).isOne();
            assertThat(count(store("report"), "SELECT count(*) FROM operational_event_journal WHERE event_type='prescription.filled'" )).isOne();
            assertThat(count(store("notification"), "SELECT count(*) FROM notification WHERE source_id=? AND template_key='OUTPATIENT_PRESCRIPTION_FILLED'", rx)).isOne();
        });
    }

    private HttpResponse<String> createRx(String gateway, String token, String key, String body) throws Exception {
        var response = HTTP.send(HttpRequest.newBuilder(URI.create(gateway + "/api/v1/pharmacy/prescriptions"))
                .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + token)
                .header("X-Correlation-Id", UUID.randomUUID().toString()).header("Content-Type", "application/json")
                .header("Idempotency-Key", key).POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        retainPublicReceipt("POST", "/api/v1/pharmacy/prescriptions", response);
        return response;
    }

    private String scopedToken(String role, String type, UUID patient) {
        var builder = Jwts.builder().subject(UUID.randomUUID().toString()).claim("role", role).claim("type", type)
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(600)));
        if (patient != null) builder.claim("patientId", patient.toString());
        return builder.signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private Process startPharmacy(int pharmacyPort, int billingPort, String eureka) throws IOException {
        return start("pharmacy", requiredJar("pharmacy"), pharmacyPort, singleStore("pharmacy", eureka),
                "--spring.profiles.active=outpatient-mvp", "--mediflow.features.care-finance-v2=true",
                "--mediflow.pharmacy.identity.enabled=true", "--mediflow.pharmacy.clinical-context.enabled=true",
                "--mediflow.pharmacy.clearance-consumer.enabled=true", "--mediflow.pharmacy.clearance-authority.enabled=true",
                "--mediflow.pharmacy.outpatient-api.enabled=true", "--mediflow.pharmacy.outpatient-events.enabled=true",
                "--mediflow.pharmacy.outpatient-events.acceptance-reference=isolated-six-service-acceptance",
                "--mediflow.pharmacy.billing.base-url=http://127.0.0.1:" + billingPort);
    }
    private static final String JWT_SECRET = "clinical-packaged-acceptance-shared-secret-0123456789";
    private static final String PASSWORD = "Acceptance-pass-2026";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");

    private final Path work = Path.of("target", "six-service-acceptance").toAbsolutePath();

    @Test
    void sixDeploymentsRunActualPaidPrescriptionAndPreserveEffectsAcrossSupportRollback() throws Exception {
        Path clinicalJar = requiredJar("clinical");
        Path coreJar = requiredJar("core");
        Path gatewayJar = requiredJar("gateway");
        Path eurekaJar = requiredJar("eureka-server");
        Path billingJar = requiredJar("billing");
        Files.createDirectories(work);
        if (reportCalendarAcceptance()) {
            Files.deleteIfExists(work.resolve("report-test-clock.txt"));
            Files.deleteIfExists(work.resolve("report-calendar-test-only.jar"));
        }
        createDatabases();

        int eurekaPort = freePort();
        int corePort = freePort();
        int clinicalPort = freePort();
        int gatewayPort = freePort();
        int billingPort = freePort();
        int pharmacyPort = freePort(), supportPort = freePort(), labPort = freePort();
        boolean frontendAcceptance = "true".equals(System.getenv("MEDIFLOW_FE_ACCEPTANCE_HOLD"));
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
                    Map.entry("SPRING_PROFILES_ACTIVE", "local"),
                    Map.entry("MEDIFLOW_DEMO_SEED_ENABLED", "false"),
                    Map.entry("MEDIFLOW_BOOTSTRAP_ADMIN_ENABLED", "true"),
                    Map.entry("MEDIFLOW_BOOTSTRAP_ADMIN_USERNAME", "acceptance.admin"),
                    Map.entry("MEDIFLOW_BOOTSTRAP_ADMIN_PASSWORD", PASSWORD),
                    Map.entry("MEDIFLOW_BOOTSTRAP_ADMIN_IDENTITY_NUMBER", "999999999901"),
                    Map.entry("MEDIFLOW_BOOTSTRAP_ADMIN_FULL_NAME", "Isolated Acceptance Admin"),
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
                    "--mediflow.clinical.outbox-poll-interval=250",
                    "--mediflow.clinical.prescription-context-lookup.enabled=true"));
            awaitHealthy(clinicalPort, "clinical");
            awaitRegistered(eurekaPort, "CLINICAL-SERVICE", "clinical");

            Path catalog = work.resolve("billing-catalog.yml");
            Files.writeString(catalog, """
                    billing:
                      care-finance:
                        prices:
                          OUTPATIENT_EXAM:
                            description: Outpatient exam acceptance
                            unit-amount: 100000.00
                          LAB:
                            description: Isolated frontend Lab acceptance
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
                    "--mediflow.billing.refunds.enabled=true",
                    "--mediflow.billing.payment-request-discovery.enabled=true",
                    "--mediflow.billing.exam-charge-consumer.enabled=true",
                    "--mediflow.billing.lab-test-charge-consumer.enabled=" + frontendAcceptance,
                    "--mediflow.billing.outpatient-publication.enabled=true",
                    "--mediflow.billing.outpatient-refund-publication.enabled=" + refundPublicationAcceptance(),
                    "--mediflow.billing.outpatient-refund-publication.acceptance-reference=Huy-isolated-refund-acceptance",
                    "--mediflow.billing.outpatient-publication.purposes="
                            + (frontendAcceptance ? "EXAM,LAB_TEST,PRESCRIPTION" : "EXAM,PRESCRIPTION"),
                    "--mediflow.billing.prescription-charge-consumer.enabled=true",
                    "--mediflow.billing.prescription-charges.allowed-price-codes=DRUG",
                    "--mediflow.billing.clearance-lookup.enabled=true",
                    "--mediflow.billing.outpatient-publication.acceptance-reference=V-CLOSE-03-isolated-EXAM"));
            awaitHealthy(billingPort, "billing");
            awaitRegistered(eurekaPort, "BILLING-SERVICE", "billing");

            processes.add(startPharmacy(pharmacyPort, billingPort, eurekaUrl));
            processes.add(start("lab", requiredJar("lab"), labPort, singleStore("lab", eurekaUrl),
                    "--mediflow.features.outpatient-mvp.enabled=true",
                    "--mediflow.features.care-finance-v2=" + frontendAcceptance,
                    "--mediflow.lab.clinical-context.enabled=" + frontendAcceptance,
                    "--mediflow.lab.clinical.base-url=http://127.0.0.1:" + clinicalPort));
            processes.add(start("support", requiredJar("support"), supportPort, supportEnvironment(eurekaUrl),
                    "--mediflow.features.care-finance-v2=true", "--mediflow.report.outpatient-consumer.enabled=true",
                    "--mediflow.report.outpatient-read.enabled=true", "--mediflow.notification.care-v1.enabled=true",
                    "--mediflow.notification.refund-consumer.enabled=" + refundPublicationAcceptance(),
                    "--mediflow.notification.outpatient-prescription-consumer.enabled=true"));
            awaitHealthy(pharmacyPort, "pharmacy"); awaitRegistered(eurekaPort, "PHARMACY-SERVICE", "pharmacy");
            awaitHealthy(labPort, "lab"); awaitRegistered(eurekaPort, "LAB-SERVICE", "lab");
            awaitHealthy(supportPort, "support"); awaitRegistered(eurekaPort, "SUPPORT-SERVICE", "support");

            processes.add(start("gateway", gatewayJar, gatewayPort, Map.of(
                    "MEDIFLOW_JWT_SECRET", JWT_SECRET),
                    "--eureka.client.service-url.defaultZone=" + eurekaUrl,
                    "--spring.profiles.active=outpatient-mvp"));
            awaitHealthy(gatewayPort, "gateway");
            awaitRegistered(eurekaPort, "GATEWAY", "gateway");

            String gateway = "http://127.0.0.1:" + gatewayPort;
            String setupAdmin = login(gateway, "acceptance.admin");
            String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            UUID departmentId = UUID.fromString(data(post(gateway, "/api/v1/org/departments", setupAdmin,
                    "departmentName", "Acceptance Clinical " + unique,
                    "abbreviation", ("AC" + unique.substring(0, 4)).toUpperCase(Locale.ROOT),
                    "departmentType", "CLINICAL", "location", "Acceptance site")).path("departmentId").asText());

            UUID doctorId = createStaff(gateway, setupAdmin, departmentId, unique, "DOCTOR", "assigned");
            UUID otherDoctorId = createStaff(gateway, setupAdmin, departmentId, unique, "DOCTOR", "other");
            UUID nurseId = createStaff(gateway, setupAdmin, departmentId, unique, "NURSE", "nurse");
            UUID cashierId = createStaff(gateway, setupAdmin, departmentId, unique, "CASHIER", "cashier");
            createAccount(gateway, setupAdmin, "doctor-" + unique, "DOCTOR", doctorId);
            createAccount(gateway, setupAdmin, "other-doctor-" + unique, "DOCTOR", otherDoctorId);
            createAccount(gateway, setupAdmin, "nurse-" + unique, "NURSE", nurseId);
            createAccount(gateway, setupAdmin, "cashier-" + unique, "CASHIER", cashierId);

            UUID patientId = UUID.fromString(data(post(gateway, "/api/v1/patients", setupAdmin,
                    "hoTen", "Acceptance Patient " + unique,
                    "ngaySinh", "1988-01-02", "gioiTinh", "M",
                    "soCmnd", digits(12), "diaChi", "Acceptance address",
                    "soDienThoai", digits(10), "email", "patient-" + unique + "@example.test",
                    "bhytSo", null)).path("maBenhNhan").asText());

            UUID otherPatientId = UUID.fromString(data(post(gateway, "/api/v1/patients", setupAdmin,
                    "hoTen", "Other Acceptance Patient " + unique,
                    "ngaySinh", "1989-01-02", "gioiTinh", "F",
                    "soCmnd", digits(12), "diaChi", "Acceptance address",
                    "soDienThoai", digits(10), "email", "other-patient-" + unique + "@example.test",
                    "bhytSo", null)).path("maBenhNhan").asText());
            createPatientAccount(gateway, setupAdmin, "patient-" + unique, patientId);
            createPatientAccount(gateway, setupAdmin, "other-patient-" + unique, otherPatientId);
            UUID managerId = createStaff(gateway, setupAdmin, departmentId, unique, "ADMINISTRATIVE", "manager");
            createAccount(gateway, setupAdmin, "manager-" + unique, "MANAGER", managerId);
            JsonNode patientSession = loginSession(gateway, "patient-" + unique);
            var patientTokens = new PatientTokens(patientSession.path("accessToken").asText(),
                    login(gateway, "other-patient-" + unique), login(gateway, "manager-" + unique),
                    patientSession.path("refreshToken").asText());
            assertThat(patientTokens.refresh()).isNotBlank();

            String nurseToken = login(gateway, "nurse-" + unique);
            String doctorToken = login(gateway, "doctor-" + unique);
            String otherDoctorToken = login(gateway, "other-doctor-" + unique);
            String cashierToken = login(gateway, "cashier-" + unique);
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

            JsonNode payment = payActualExam(gateway, appointmentId, patientId, cashierToken, nurseToken);
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertThat(count(clinicalDb,
                        "SELECT count(*) FROM exam_clearance WHERE appointment_id=? AND patient_id=?",
                        appointmentId, patientId)).isOne();
                assertThat(data(get(gateway, "/api/v1/appointments/" + appointmentId, nurseToken))
                        .path("status").asText()).isEqualTo("READY_FOR_EXAM");
            });

            assertExamRollbackAndConcurrentStart(gateway, appointmentId, doctorToken, clinicalDb);
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
            HttpResponse<String> repeatedStart = request(gateway, "PUT",
                    "/api/v1/appointments/" + appointmentId + "/start-exam", doctorToken, "{}");
            assertClinicalFailure(repeatedStart, 422, "CLINICAL_INVALID_STATUS_TRANSITION");
            assertThat(count(clinicalDb, "SELECT count(*) FROM medical_record WHERE appointment_id=?", appointmentId)).isOne();
            byte[] grant = clinicalDb.queryForObject("""
                    SELECT raw_body FROM consumer_inbox_delivery
                    WHERE routing_key='financial.clearance.granted'
                      AND convert_from(raw_body, 'UTF8')::jsonb->'payload'->>'appointmentId'=?
                    """, byte[].class, appointmentId.toString());
            JsonNode payload = JSON.readTree(grant).path("payload");
            assertThat(payload.path("patientId").asText()).isEqualTo(patientId.toString());
            assertThat(payload.path("careEpisodeId").asText()).isEqualTo(appointmentId.toString());
            assertThat(payload.path("purpose").asText()).isEqualTo("EXAM");
            assertThat(payload.path("accountId").asText()).isEqualTo(payment.path("accountId").asText());
            assertThat(data(get(gateway, "/api/v1/appointments/" + appointmentId, nurseToken))
                    .path("examClearanceId").asText()).isEqualTo(payload.path("clearanceId").asText());
            if ("true".equals(System.getenv("MEDIFLOW_FE_ACCEPTANCE_HOLD"))) {
                holdForFrontendAcceptance(gateway, setupAdmin, unique, departmentId, doctorId, patientId,
                        appointmentId, doctorToken, supportPort, eurekaUrl, processes);
                return;
            }
            exercisePrescription(gateway, setupAdmin, doctorToken, cashierToken, nurseToken, patientTokens,
                    patientId, doctorId, departmentId, appointmentId, recordId, supportPort, pharmacyPort, billingPort, eurekaUrl, processes);
            exerciseRefund(gateway, setupAdmin, doctorToken, cashierToken, patientTokens.own(),
                    patientId, doctorId, departmentId, appointmentId, recordId);
            if (reportCalendarAcceptance()) {
                exerciseAcceptedReport(gateway, setupAdmin, doctorToken, departmentId, unique);
            }
            Path evidence = Path.of("target", "six-service-paid-exam-evidence.json");
            Files.createDirectories(evidence.getParent());
            Files.writeString(evidence, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                    "appointmentId", appointmentId, "recordId", recordId, "payment", payment,
                    "recordInsertRollbackVerified", true, "concurrentExamStatuses", List.of(200, 422),
                    "grant", JSON.readTree(grant), "grantSha256", java.util.HexFormat.of().formatHex(
                            java.security.MessageDigest.getInstance("SHA-256").digest(grant)))));
        } finally {
            for (ServerSocket reservation : reservedPorts.values()) reservation.close();
            reservedPorts.clear();
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

    /** Opt-in, disposable browser environment; normal CI acceptance still runs unchanged. */
    private void holdForFrontendAcceptance(String gateway, String bootstrap, String unique, UUID department,
            UUID doctor, UUID patient, UUID appointment, String doctorToken, int supportPort,
            String eureka, List<Process> processes) throws Exception {
        UUID adminStaff = createStaff(gateway, bootstrap, department, unique, "ADMINISTRATIVE", "admin");
        UUID pharmacistStaff = createStaff(gateway, bootstrap, department, unique, "PHARMACIST", "pharmacist");
        createAccount(gateway, bootstrap, "admin-" + unique, "ADMIN", adminStaff);
        createAccount(gateway, bootstrap, "pharmacist-" + unique, "PHARMACIST", pharmacistStaff);
        // All acceptance commands after bootstrap use actual issuer logins, including ADMIN.
        String admin = login(gateway, "admin-" + unique);
        String pharmacist = login(gateway, "pharmacist-" + unique);
        String otherDoctor = login(gateway, "other-doctor-" + unique);
        JsonNode records = data(get(gateway, "/api/v1/records/patient/" + patient, doctorToken));
        var matching = new ArrayList<JsonNode>();
        records.forEach(record -> { if (record.path("appointmentId").asText().equals(appointment.toString())) matching.add(record); });
        assertThat(matching).hasSize(1);
        JsonNode record = matching.get(0);
        assertThat(record.path("doctorId").asText()).isEqualTo(doctor.toString());
        // This genuine test Lab order exposes an explicit episode through a public DTO.
        // Do not infer careEpisodeId from appointmentId or call service-only lookup as a human.
        JsonNode source = data(post(gateway, "/api/v1/records/" + record.path("recordId").asText() + "/lab-orders",
                doctorToken, "labType", "CBC", "requestedDate", LocalDate.now().toString(), "priceCode", "LAB"));
        assertThat(source.path("recordId")).isEqualTo(record.path("recordId"));
        assertThat(source.path("careEpisodeType").asText()).isEqualTo("OUTPATIENT_VISIT");
        JsonNode drug = data(post(gateway, "/api/v1/pharmacy/drugs", admin,
                "drugName", "FE acceptance " + unique, "unit", "tablet", "price", 10000,
                "stockQuantity", 40, "expiryDate", LocalDate.now().plusYears(1).toString(), "lowStockThreshold", 0));
        var body = object("recordId", record.path("recordId").asText(), "patientId", patient, "doctorId", doctor,
                "departmentId", department, "prescribedDate", LocalDate.now().toString(),
                "lines", List.of(Map.of("drugId", drug.path("drugId").asText(), "quantity", 3, "dosage", "Acceptance only")),
                "careContractVersion", 1, "careContext", "OUTPATIENT", "careEpisodeType", "OUTPATIENT_VISIT",
                "careEpisodeId", source.path("careEpisodeId").asText(), "priceCode", "DRUG");
        String json = JSON.writeValueAsString(body), key = UUID.randomUUID().toString();
        var checks = new ArrayList<Map<String, Object>>();
        recordCheck(checks, "foreign-doctor-create", createRx(gateway, otherDoctor, key, json), 403);
        JsonNode cancelledRx = data(createRx(gateway, doctorToken, key, json));
        String cancel = "/api/v1/pharmacy/prescriptions/" + cancelledRx.path("prescriptionId").asText() + "/cancel";
        String reason = JSON.writeValueAsString(Map.of("reason", "Isolated FE acceptance cancellation"));
        recordCheck(checks, "foreign-doctor-cancel", request(gateway, "PUT", cancel, otherDoctor, reason), 403);
        recordCheck(checks, "pharmacist-cancel", request(gateway, "PUT", cancel, pharmacist, reason), 403);
        var result = request(gateway, "PUT", cancel, doctorToken, reason);
        recordCheck(checks, "assigned-doctor-cancel", result, 200);
        assertThat(data(result).path("releasedReservations").asInt()).isEqualTo(1);
        var manifest = object("gateway", gateway, "createdAt", Instant.now(), "unique", unique,
                "accounts", Map.of("ADMIN", "admin-" + unique, "DOCTOR", "doctor-" + unique,
                        "OTHER_DOCTOR", "other-doctor-" + unique, "PHARMACIST", "pharmacist-" + unique,
                        "CASHIER", "cashier-" + unique, "NURSE", "nurse-" + unique),
                "sourceRecord", record, "sourceOrder", source, "drug", drug, "prescriptionRequest", body,
                "cancelledPrescriptionId", cancelledRx.path("prescriptionId").asText(), "roleChecks", checks,
                "pricePolicy", "mediflow.billing.prescription-charges.allowed-price-codes=DRUG");
        Path manifestPath = work.resolve("fe-browser-manifest.json");
        Path commandPath = work.resolve("fe-browser-command.json");
        Path resultPath = work.resolve("fe-browser-result.json");
        Files.deleteIfExists(commandPath);
        Files.deleteIfExists(resultPath);
        Files.writeString(manifestPath, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(manifest));
        long deadline = System.nanoTime() + Duration.ofMinutes(60).toNanos();
        while (System.nanoTime() < deadline) {
            assertProcessRunning("gateway");
            if (Files.exists(commandPath)) {
                JsonNode command = JSON.readTree(Files.readString(commandPath));
                Files.delete(commandPath);
                String action = command.path("action").asText();
                if (action.equals("STOP")) return;
                if (action.equals("PAUSE_SUPPORT") || action.equals("RESUME_SUPPORT")) {
                    if (action.equals("PAUSE_SUPPORT")) stop(processByName("support"));
                    else {
                        assertThat(processByName("support").isAlive()).isFalse();
                        processes.add(start("support", requiredJar("support"), supportPort, supportEnvironment(eureka),
                                "--mediflow.features.care-finance-v2=true", "--mediflow.report.outpatient-consumer.enabled=true",
                                "--mediflow.report.outpatient-read.enabled=true", "--mediflow.notification.care-v1.enabled=true",
                                "--mediflow.notification.outpatient-prescription-consumer.enabled=true"));
                        awaitHealthy(supportPort, "support");
                    }
                    Files.writeString(resultPath, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(
                            object("action", action, "checkedAt", Instant.now(), "supportRunning", processByName("support").isAlive())));
                    continue;
                }
                UUID rx = UUID.fromString(command.path("prescriptionId").asText());
                String rxPath = "/api/v1/pharmacy/prescriptions/" + rx;
                String freshAdmin = login(gateway, "admin-" + unique);
                String freshPharmacist = login(gateway, "pharmacist-" + unique);
                var evidence = new ArrayList<Map<String, Object>>();
                if (action.equals("PAY")) {
                    recordCheck(evidence, "doctor-dispense-denied", request(gateway, "PUT", rxPath + "/dispense",
                            login(gateway, "doctor-" + unique), null), 403);
                    recordCheck(evidence, "unpaid-pharmacist-dispense", request(gateway, "PUT", rxPath + "/dispense",
                            freshPharmacist, null), 422);
                    String cashier = login(gateway, "cashier-" + unique);
                    String discovery = "/api/v1/billing/payment-requests/by-source?purpose=PRESCRIPTION&sourceId=" + rx
                            + "&patientId=" + patient + "&careEpisodeId=" + source.path("careEpisodeId").asText();
                    JsonNode[] found = new JsonNode[1];
                    await().atMost(Duration.ofSeconds(45)).untilAsserted(() -> found[0] = data(request(gateway, "GET", discovery, cashier, null)));
                    JsonNode paymentRequest = found[0];
                    String paymentPath = "/api/v1/billing/payment-requests/" + paymentRequest.path("paymentRequestId").asText() + "/payments";
                    String paymentBody = JSON.writeValueAsString(Map.of("idempotencyKey", "fe-paid-" + rx,
                            "amount", paymentRequest.path("remainingAmount").decimalValue(), "currency", "VND", "paymentMethod", "CASH"));
                    recordCheck(evidence, "actual-cashier-payment", request(gateway, "POST", paymentPath, cashier, paymentBody), 200);
                    recordCheck(evidence, "duplicate-payment", request(gateway, "POST", paymentPath, cashier, paymentBody), 200);
                    await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(data(request(gateway, "GET", rxPath, freshPharmacist, null))
                            .path("paymentConfirmed").asBoolean()).isTrue());
                    recordCheck(evidence, "paid-still-not-dispensed", request(gateway, "GET", rxPath, freshPharmacist, null), 200);
                    assertThat(data(request(gateway, "GET", rxPath, freshPharmacist, null)).path("dispenseStatus").asText()).isEqualTo("PENDING");
                    assertThat(data(request(gateway, "GET", "/api/v1/pharmacy/drugs/" + drug.path("drugId").asText(), freshAdmin, null))
                            .path("stockQuantity").asInt()).isEqualTo(40);
                } else if (action.equals("VERIFY_DISPENSE")) {
                    recordCheck(evidence, "terminal-dispense-retry", request(gateway, "PUT", rxPath + "/dispense", freshPharmacist, null), 200);
                    assertThat(count(store("pharmacy"), "SELECT count(*) FROM dispense_slip WHERE prescription_id=?", rx)).isOne();
                    recordCheck(evidence, "final-drug-stock", request(gateway, "GET", "/api/v1/pharmacy/drugs/" + drug.path("drugId").asText(), freshAdmin, null), 200);
                    assertThat(data(request(gateway, "GET", "/api/v1/pharmacy/drugs/" + drug.path("drugId").asText(), freshAdmin, null))
                            .path("stockQuantity").asInt()).isEqualTo(37);
                    assertThat(count(store("pharmacy"), "SELECT count(*) FROM pharmacy_event_outbox WHERE aggregate_id=? AND routing_key='prescription.filled'", rx)).isOne();
                    await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(count(store("clinical"),
                            "SELECT count(*) FROM attached_result WHERE record_id=? AND type='PRESCRIPTION' AND reference_id=?",
                            UUID.fromString(record.path("recordId").asText()), rx)).isOne());
                    String bytes = store("pharmacy").queryForObject(
                            "SELECT payload FROM pharmacy_event_outbox WHERE aggregate_id=? AND routing_key='prescription.filled'", String.class, rx);
                    UUID eventId = store("pharmacy").queryForObject(
                            "SELECT event_id FROM pharmacy_event_outbox WHERE aggregate_id=? AND routing_key='prescription.filled'", UUID.class, rx);
                    String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                            .digest(bytes.getBytes(StandardCharsets.UTF_8)));
                    Files.writeString(work.resolve("fe-replay-origin.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(
                            object("eventId", eventId, "prescriptionId", rx, "routingKey", "prescription.filled", "payloadSha256", hash)));
                } else if (action.equals("VERIFY_REPLAY")) {
                    JsonNode origin = JSON.readTree(Files.readString(work.resolve("fe-replay-origin.json")));
                    UUID eventId = UUID.fromString(origin.path("eventId").asText());
                    await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(count(store("pharmacy"),
                            "SELECT count(*) FROM pharmacy_event_outbox WHERE event_id=? AND published_at IS NOT NULL", eventId)).isOne());
                    String bytes = store("pharmacy").queryForObject("SELECT payload FROM pharmacy_event_outbox WHERE event_id=?", String.class, eventId);
                    assertThat(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                            .digest(bytes.getBytes(StandardCharsets.UTF_8)))).isEqualTo(origin.path("payloadSha256").asText());
                    assertThat(count(store("clinical"), "SELECT count(*) FROM attached_result WHERE record_id=? AND type='PRESCRIPTION' AND reference_id=?",
                            UUID.fromString(record.path("recordId").asText()), rx)).isOne();
                    assertThat(count(store("report"), "SELECT count(*) FROM operational_event_journal WHERE event_id=?", eventId)).isOne();
                    assertThat(count(store("notification"), "SELECT count(*) FROM notification WHERE source_id=? AND template_key='OUTPATIENT_PRESCRIPTION_FILLED'", rx)).isOne();
                    assertThat(count(store("pharmacy"), "SELECT count(*) FROM dispense_slip WHERE prescription_id=? AND status='DISPENSED'", rx)).isOne();
                    recordCheck(evidence, "after-replay-stock", request(gateway, "GET", "/api/v1/pharmacy/drugs/" + drug.path("drugId").asText(), freshAdmin, null), 200);
                    assertThat(data(request(gateway, "GET", "/api/v1/pharmacy/drugs/" + drug.path("drugId").asText(), freshAdmin, null))
                            .path("stockQuantity").asInt()).isEqualTo(37);
                    evidence.add(object("scenario", "replay-effects", "eventId", eventId, "payloadSha256", origin.path("payloadSha256").asText(),
                            "clinicalAttachments", 1, "reportJournalEntries", 1, "notifications", 1, "dispenseSlips", 1, "stock", 37));
                } else throw new IllegalArgumentException("Unknown FE acceptance action");
                Files.writeString(resultPath, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(object(
                        "action", action, "prescriptionId", rx, "checkedAt", Instant.now(), "checks", evidence)));
            }
            Thread.sleep(500);
        }
        throw new AssertionError("FE browser acceptance timed out; disposable processes will be stopped");
    }

    private void recordCheck(List<Map<String, Object>> checks, String scenario, HttpResponse<String> response, int expected)
            throws IOException {
        assertThat(response.statusCode()).as(scenario + ": " + response.body()).isEqualTo(expected);
        JsonNode body = JSON.readTree(response.body());
        checks.add(object("scenario", scenario, "httpStatus", response.statusCode(),
                "correlationId", body.path("correlationId").asText(), "response", body));
    }

    private void assertExamRollbackAndConcurrentStart(String gateway, UUID appointment, String doctor,
            JdbcTemplate clinicalDb) throws Exception {
        String path = "/api/v1/appointments/" + appointment + "/start-exam";
        int recordsPublished = count(clinicalDb,
                "SELECT count(*) FROM clinical_outbox_event WHERE event_type='medicalrecord.created'");
        // Fault injection belongs only to this disposable test database. The application still
        // performs every business write through its public API and normal transaction boundary.
        clinicalDb.execute("""
                CREATE FUNCTION acceptance_fail_exam_commit() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'isolated acceptance failure after record insert'; END; $$
                """);
        // records.saveAndFlush() has already inserted the record before this appointment update.
        clinicalDb.execute("CREATE TRIGGER acceptance_fail_exam_commit AFTER UPDATE OF status ON appointment "
                + "FOR EACH ROW WHEN (NEW.status='IN_EXAM') EXECUTE FUNCTION acceptance_fail_exam_commit()");
        try {
            HttpResponse<String> failed = request(gateway, "PUT", path, doctor, "{}");
            assertThat(failed.statusCode()).as(failed.body()).isEqualTo(500);
            assertThat(data(get(gateway, "/api/v1/appointments/" + appointment, doctor))
                    .path("status").asText()).isEqualTo("READY_FOR_EXAM");
            assertThat(count(clinicalDb, "SELECT count(*) FROM medical_record WHERE appointment_id=?", appointment)).isZero();
            assertThat(count(clinicalDb,
                    "SELECT count(*) FROM clinical_outbox_event WHERE event_type='medicalrecord.created'"))
                    .isEqualTo(recordsPublished);
        } finally {
            clinicalDb.execute("DROP TRIGGER acceptance_fail_exam_commit ON appointment");
            clinicalDb.execute("DROP FUNCTION acceptance_fail_exam_commit()");
        }
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<HttpResponse<String>> first = pool.submit(() -> { ready.countDown(); start.await(); return request(gateway, "PUT", path, doctor, "{}"); });
            Future<HttpResponse<String>> second = pool.submit(() -> { ready.countDown(); start.await(); return request(gateway, "PUT", path, doctor, "{}"); });
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            HttpResponse<String> a = first.get(45, java.util.concurrent.TimeUnit.SECONDS);
            HttpResponse<String> b = second.get(45, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(List.of(a.statusCode(), b.statusCode())).containsExactlyInAnyOrder(200, 422);
            assertThat(data(a.statusCode() == 200 ? a : b).path("status").asText()).isEqualTo("IN_EXAM");
            assertClinicalFailure(a.statusCode() == 422 ? a : b, 422, "CLINICAL_INVALID_STATUS_TRANSITION");
        } finally { pool.shutdownNow(); }
    }

    private void exerciseRefund(String gateway, String admin, String doctor, String cashier, String patientToken,
            UUID patient, UUID prescriber, UUID department, UUID episode, UUID record) throws Exception {
        JsonNode drug = data(post(gateway, "/api/v1/pharmacy/drugs", admin,
                "drugName", "Refund acceptance drug", "unit", "tablet", "price", 10000,
                "stockQuantity", 20, "expiryDate", LocalDate.now().plusYears(1).toString(), "lowStockThreshold", 0));
        UUID drugId = UUID.fromString(drug.path("drugId").asText());
        String body = JSON.writeValueAsString(object("recordId", record, "patientId", patient, "doctorId", prescriber,
                "departmentId", department, "prescribedDate", LocalDate.now().toString(), "lines",
                List.of(Map.of("drugId", drugId, "quantity", 3, "dosage", "Refund acceptance")),
                "careContractVersion", 1, "careContext", "OUTPATIENT", "careEpisodeType", "OUTPATIENT_VISIT",
                "careEpisodeId", episode, "priceCode", "DRUG"));
        UUID rx = UUID.fromString(data(createRx(gateway, doctor, UUID.randomUUID().toString(), body))
                .path("prescriptionId").asText());
        String discovery = "/api/v1/billing/payment-requests/by-source?purpose=PRESCRIPTION&sourceId=" + rx
                + "&patientId=" + patient + "&careEpisodeId=" + episode;
        JsonNode[] found = new JsonNode[1];
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> found[0] = data(get(gateway, discovery, cashier)));
        String paymentPath = "/api/v1/billing/payment-requests/" + found[0].path("paymentRequestId").asText() + "/payments";
        JsonNode payment = data(request(gateway, "POST", paymentPath, cashier, JSON.writeValueAsString(Map.of(
                "idempotencyKey", "refund-payment-" + rx, "amount", 30000, "currency", "VND", "paymentMethod", "CASH"))));
        UUID original = UUID.fromString(payment.path("transactionId").asText());
        data(request(gateway, "PUT", "/api/v1/pharmacy/prescriptions/" + rx + "/cancel", doctor,
                "{\"reason\":\"Isolated paid cancellation acceptance\"}"));
        String duePath = "/api/v1/billing/prescription-adjustments/" + rx + "/refunds-due";
        await().atMost(Duration.ofSeconds(45)).untilAsserted(() -> {
            JsonNode adjustment = data(get(gateway, duePath, cashier));
            assertThat(adjustment.path("status").asText()).isEqualTo("APPLIED");
            assertThat(adjustment.path("refundsDue")).hasSize(1);
            assertThat(adjustment.path("refundsDue").get(0).path("originalTransactionId").asText()).isEqualTo(original.toString());
            assertThat(adjustment.path("refundsDue").get(0).path("amount").decimalValue()).isEqualByComparingTo("30000.00");
        });
        assertThat(data(get(gateway, duePath, admin)).path("refundsDue")).hasSize(1);
        String refundPath = "/api/v1/billing/transactions/" + original + "/refunds";
        String firstBody = refundBody(UUID.randomUUID().toString(), 20000);
        String secondBody = refundBody(UUID.randomUUID().toString(), 20000);
        for (String denied : List.of(doctor, patientToken,
                scopedToken("SYSTEM", "service", null))) {
            assertThat(request(gateway, "GET", duePath, denied, null).statusCode()).isEqualTo(403);
            assertThat(request(gateway, "POST", refundPath, denied, firstBody).statusCode()).isEqualTo(403);
        }
        for (String denied : List.of(scopedToken("ADMIN", "refresh", null), scopedToken("SYSTEM", "access", null), "invalid")) {
            assertThat(request(gateway, "GET", duePath, denied, null).statusCode()).isEqualTo(401);
            assertThat(request(gateway, "POST", refundPath, denied, firstBody).statusCode()).isEqualTo(401);
        }
        assertThat(HTTP.send(HttpRequest.newBuilder(URI.create(gateway + duePath)).GET().build(),
                HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
        assertThat(request(gateway, "POST", duePath, admin, firstBody).statusCode()).isEqualTo(403);
        assertThat(request(gateway, "GET", refundPath, admin, null).statusCode()).isEqualTo(403);
        assertThat(request(gateway, "GET", duePath + "/extra", admin, null).statusCode()).isEqualTo(403);
        assertThat(request(gateway, "POST", refundPath + "/extra", admin, firstBody).statusCode()).isEqualTo(403);
        assertThat(request(gateway, "GET", "/api/v1/billing/prescription-adjustments/refunds-due", admin, null).statusCode()).isEqualTo(403);
        assertThat(request(gateway, "POST", "/api/v1/billing/transactions/refunds", admin, firstBody).statusCode()).isEqualTo(403);
        assertThat(count(store("billing"), "SELECT count(*) FROM payment_transaction WHERE original_transaction_id=? AND transaction_type='REFUND'", original)).isZero();

        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        JsonNode refund;
        String winningBody;
        try {
            Future<HttpResponse<String>> first = pool.submit(() -> { ready.countDown(); start.await(); return request(gateway, "POST", refundPath, cashier, firstBody); });
            Future<HttpResponse<String>> second = pool.submit(() -> { ready.countDown(); start.await(); return request(gateway, "POST", refundPath, cashier, secondBody); });
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            HttpResponse<String> a = first.get(45, java.util.concurrent.TimeUnit.SECONDS);
            HttpResponse<String> b = second.get(45, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(List.of(a.statusCode(), b.statusCode())).containsExactlyInAnyOrder(201, 422);
            refund = data(a.statusCode() == 201 ? a : b);
            winningBody = a.statusCode() == 201 ? firstBody : secondBody;
            JsonNode rejected = JSON.readTree((a.statusCode() == 422 ? a : b).body());
            assertThat(rejected.path("error").path("code").asText()).isEqualTo("BILLING_REFUND_EXCEEDS_PAYMENT");
        } finally { pool.shutdownNow(); }
        assertThat(data(request(gateway, "POST", refundPath, cashier, winningBody)).path("refundTransactionId"))
                .isEqualTo(refund.path("refundTransactionId"));
        assertThat(request(gateway, "POST", refundPath, admin, winningBody).statusCode()).isEqualTo(409);
        UUID refundId = UUID.fromString(refund.path("refundTransactionId").asText());
        String actor = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(cashier).getPayload().getSubject();
        assertThat(store("billing").queryForObject("SELECT actor_account_id FROM payment_transaction WHERE transaction_id=?", UUID.class, refundId))
                .isEqualTo(UUID.fromString(actor));
        assertThat(data(get(gateway, duePath, cashier)).path("refundsDue").get(0).path("amount").decimalValue()).isEqualByComparingTo("10000.00");
        String finalKey = UUID.randomUUID().toString();
        String finalBody = refundBody(finalKey, 10000);
        String correlation = UUID.randomUUID().toString();
        HttpResponse<String> completed = HTTP.send(HttpRequest.newBuilder(URI.create(gateway + refundPath))
                .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + admin)
                .header("Content-Type", "application/json").header("X-Correlation-Id", correlation)
                .POST(HttpRequest.BodyPublishers.ofString(finalBody)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(completed.statusCode()).isEqualTo(201);
        assertThat(completed.headers().firstValue("X-Correlation-Id")).contains(correlation);
        assertThat(JSON.readTree(completed.body()).path("correlationId").asText()).isEqualTo(correlation);
        JsonNode last = data(completed);
        assertThat(last.path("originalTransactionId").asText()).isEqualTo(original.toString());
        assertThat(last.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(data(request(gateway, "POST", refundPath, admin, finalBody)).path("refundTransactionId"))
                .isEqualTo(last.path("refundTransactionId"));
        assertThat(request(gateway, "POST", refundPath, admin, refundBody(finalKey, 9000)).statusCode()).isEqualTo(409);
        assertThat(request(gateway, "POST", refundPath, admin, refundBody(UUID.randomUUID().toString(), 1)).statusCode()).isEqualTo(422);
        assertThat(data(get(gateway, duePath, cashier)).path("refundsDue")).isEmpty();
        assertThat(count(store("billing"), "SELECT count(*) FROM payment_transaction WHERE original_transaction_id=? AND transaction_type='REFUND'", original)).isEqualTo(2);
        assertThat(store("billing").queryForObject("SELECT sum(amount) FROM payment_transaction WHERE original_transaction_id=? AND transaction_type='REFUND'", java.math.BigDecimal.class, original))
                .isEqualByComparingTo("30000.00");
        assertThat(count(store("billing"), """
                SELECT count(*) FROM billing_event_outbox WHERE routing_key='payment.refunded'
                  AND payload::jsonb->'payload'->>'originalTransactionId'=? AND publication_enabled=?
                """, original.toString(), refundPublicationAcceptance())).isEqualTo(2);
        if (refundPublicationAcceptance()) {
            await().atMost(Duration.ofSeconds(45)).untilAsserted(() -> {
                assertThat(count(store("billing"), """
                        SELECT count(*) FROM billing_event_outbox WHERE routing_key='payment.refunded'
                          AND payload::jsonb->'payload'->>'originalTransactionId'=?
                          AND publication_enabled AND published_at IS NOT NULL
                        """, original.toString())).isEqualTo(2);
                assertThat(count(store("notification"), """
                        SELECT count(*) FROM notification WHERE template_key='PAYMENT_REFUNDED' AND source_id IN (?,?)
                        """, refundId, UUID.fromString(last.path("refundTransactionId").asText()))).isEqualTo(2);
                assertThat(count(store("report"), """
                        SELECT count(*) FROM report_cash_refund WHERE original_transaction_id=? AND state='APPLIED'
                        """, original)).isEqualTo(2);
            });
        }
        assertThat(store("pharmacy").queryForObject("SELECT stock_quantity FROM drug WHERE drug_id=?", Integer.class, drugId)).isEqualTo(20);
        assertThat(request(gateway, "PUT", "/api/v1/pharmacy/prescriptions/" + rx + "/dispense", admin, null).statusCode()).isEqualTo(422);
        Files.writeString(work.resolve("gateway-refund-evidence.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(
                Map.of("prescriptionId", rx, "originalPayment", payment, "firstRefund", refund, "finalRefund", last,
                        "raceStatuses", List.of(201, 422), "signedActorVerified", true, "correlationId", correlation,
                        "remainingObligations", 0, "stock", 20)));
    }

    private static boolean refundPublicationAcceptance() {
        return "true".equalsIgnoreCase(System.getenv("MEDIFLOW_FE_REFUND_ACCEPTANCE"));
    }

    private static boolean reportCalendarAcceptance() {
        return "true".equalsIgnoreCase(System.getenv("MEDIFLOW_FE_REPORT_CALENDAR_ACCEPTANCE"));
    }

    private final List<Map<String,Object>> publicReceipts = java.util.Collections.synchronizedList(new ArrayList<>());

    private void retainPublicReceipt(String method, String path, HttpResponse<String> response) throws IOException {
        if (!reportCalendarAcceptance() || response.statusCode() < 200 || response.statusCode() >= 300) return;
        String source = path.startsWith("/api/v1/billing/")
                ? (path.contains("/refund") ? "BILLING_REFUND" : "BILLING_CASH")
                : path.startsWith("/api/v1/pharmacy/") ? "PHARMACY"
                : path.startsWith("/api/v1/appointments") || path.startsWith("/api/v1/records") ? "CLINICAL"
                : path.startsWith("/api/v1/lab/") ? "LAB" : null;
        if (source != null) publicReceipts.add(object("source", source, "method", method, "path", path,
                "status", response.statusCode(), "recordedAt", Instant.now().toString(),
                "response", JSON.readTree(response.body())));
    }

    /** Finite fresh-container scenario, not production source completeness or historical cutover. */
    private void exerciseAcceptedReport(String gateway, String bootstrap, String doctor,
            UUID department, String unique) throws Exception {
        assertThat(refundPublicationAcceptance()).as("calendar acceptance needs real refund delivery").isTrue();
        LocalDate day = LocalDate.now(java.time.ZoneId.of("Asia/Bangkok"));
        int year = day.getYear();
        UUID staff = createStaff(gateway, bootstrap, department, unique, "ADMINISTRATIVE", "report-admin");
        UUID managerStaff = createStaff(gateway, bootstrap, department, unique, "ADMINISTRATIVE", "report-manager");
        String adminName = "report-admin-" + unique, managerName = "report-manager-" + unique;
        createAccount(gateway, bootstrap, adminName, "ADMIN", staff);
        createAccount(gateway, bootstrap, managerName, "MANAGER", managerStaff);
        String admin = login(gateway, adminName), manager = login(gateway, managerName);
        String scope = "&departmentId=" + department;
        String daily = "/api/v1/reports/outpatient/daily?fromDate=" + day + "&toDate=" + day + scope;
        String monthly = "/api/v1/reports/outpatient/monthly?year=" + year + scope;
        var checks = new ArrayList<Map<String,Object>>();
        recordCheck(checks, "missing-coverage-before-approval", request(gateway,"GET",daily,admin,null),404);
        String previewPath = "/api/v1/reports/outpatient/coverage/preview?fromDate=" + year
                + "-01-01&toDate=" + year + "-12-31" + scope;
        JsonNode[] snapshot = new JsonNode[1];
        var expectedRows = Map.of("CLINICAL",0,"LAB",0,"PHARMACY",2,"BILLING_CASH",3,"BILLING_REFUND",2);
        await().atMost(Duration.ofSeconds(45)).untilAsserted(() -> {
            snapshot[0] = data(request(gateway,"GET",previewPath,admin,null));
            assertThat(snapshot[0].path("pendingOrInvalid").asBoolean()).isFalse();
            expectedRows.forEach((source,count) -> assertThat(snapshot[0].path("sources").path(source)
                    .path("rowCount").asInt()).as(source).isEqualTo(count));
        });
        // Freeze all source writers. Only Core identity, Gateway and Support remain for browser reads.
        for (String source : List.of("clinical","lab","pharmacy","billing")) stop(processByName(source));
        List<Map<String,Object>> receipts;
        synchronized (publicReceipts) { receipts = List.copyOf(publicReceipts); }
        var payments = new java.util.TreeMap<String,JsonNode>();
        var refunds = new java.util.TreeMap<String,JsonNode>();
        for (var receipt : receipts) {
            JsonNode value = ((JsonNode)receipt.get("response")).path("data");
            if (receipt.get("method").equals("POST") && ((String)receipt.get("path")).endsWith("/payments")
                    && value.hasNonNull("transactionId")) payments.put(value.path("transactionId").asText(),value);
            if (receipt.get("method").equals("POST") && ((String)receipt.get("path")).endsWith("/refunds")
                    && value.hasNonNull("refundTransactionId")) refunds.put(value.path("refundTransactionId").asText(),value);
        }
        assertThat(payments).hasSize(3); assertThat(refunds).hasSize(2);
        var cash = payments.values().stream().map(v -> v.path("amount").decimalValue())
                .reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);
        var refundTotal = refunds.values().stream().map(v -> v.path("amount").decimalValue())
                .reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);
        assertThat(cash).isEqualByComparingTo("160000.00");
        assertThat(refundTotal).isEqualByComparingTo("30000.00");
        Path exports = work.resolve("report-source-exports"); Files.createDirectories(exports);
        var proofs = new java.util.TreeMap<String,Object>();
        for (String source : expectedRows.keySet()) {
            Path file = exports.resolve(source + ".json");
            String bytes = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(object(
                    "provenance", "ISOLATED_FRESH_CONTAINER_PUBLIC_COMMAND_JOURNAL_NOT_PRODUCTION_EXPORT",
                    "scope", department, "fromDate", year + "-01-01", "toDate", year + "-12-31",
                    "source", source, "zoneId", "Asia/Bangkok", "currency", "VND",
                    "allWritersStopped", true, "wallTimeSealedAt", Instant.now().toString(),
                    "completedOperationEvidenceRows", expectedRows.get(source),
                    "receipts", receipts.stream().filter(r -> source.equals(r.get("source"))).toList()));
            Files.writeString(file,bytes);
            String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(bytes.getBytes(StandardCharsets.UTF_8)));
            proofs.put(source,object("reference","ISOLATED_CALENDAR_TEST:" + unique + "/" + source + ".json",
                    "exportSha256",hash,"rowCount",expectedRows.get(source),
                    "localFingerprint",snapshot[0].path("sources").path(source).path("fingerprint").asText()));
        }
        // Advance only an injected test Clock; production uses the original system clock and guards.
        Instant calendarEnd = LocalDate.of(year+1,1,2).atStartOfDay(java.time.ZoneId.of("Asia/Bangkok")).toInstant();
        Files.writeString(work.resolve("report-test-clock.txt"),calendarEnd.toString());
        var approval = object("requestId",UUID.randomUUID(),"fromDate",year+"-01-01","toDate",year+"-12-31",
                "departmentId",department,"zoneId","Asia/Bangkok","currency","VND","sources",proofs,
                "sourceObservedAt",calendarEnd,"reconciledAt",calendarEnd,
                "acceptanceReference","ISOLATED_CALENDAR_TEST:"+unique+":sealed-public-receipts",
                "expectedFingerprint",snapshot[0].path("fingerprint").asText());
        String approvalBytes = JSON.writeValueAsString(approval);
        String approvalPath = "/api/v1/reports/outpatient/coverage/acceptances";
        recordCheck(checks,"manager-cannot-approve",request(gateway,"POST",approvalPath,manager,approvalBytes),403);
        HttpResponse<String> accepted = request(gateway,"POST",approvalPath,admin,approvalBytes);
        recordCheck(checks,"admin-accepts-reviewed-test-scenario",accepted,200);
        assertThat(data(request(gateway,"POST",approvalPath,admin,approvalBytes))).isEqualTo(data(accepted));
        for (String token : List.of(admin,manager)) {
            HttpResponse<String> result = request(gateway,"GET",daily,token,null);
            recordCheck(checks,"authorized-daily",result,200);
            JsonNode period = data(result).path("periods").get(0);
            assertReportPeriod(period,cash,refundTotal);
            HttpResponse<String> months = request(gateway,"GET",monthly,token,null);
            recordCheck(checks,"authorized-monthly",months,200);
            assertThat(data(months).path("periods")).hasSize(12);
            assertReportPeriod(data(months).path("periods").get(day.getMonthValue()-1),cash,refundTotal);
        }
        recordCheck(checks,"doctor-daily-denied",request(gateway,"GET",daily,doctor,null),403);
        recordCheck(checks,"doctor-monthly-denied",request(gateway,"GET",monthly,doctor,null),403);
        recordCheck(checks,"unapproved-scope-unavailable",request(gateway,"GET",daily.replace(scope,""),admin,null),404);
        Files.writeString(work.resolve("report-calendar-api.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(
                object("scenario","ISOLATED_CALENDAR_TEST_NOT_PRODUCTION_COVERAGE","wallTime",Instant.now(),
                        "reportApplicationClock",calendarEnd,"systemAndJwtClocksUnchanged",true,
                        "approval",approval,"checks",checks)));
        Files.writeString(work.resolve("report-browser-manifest.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(
                object("gateway",gateway,"unique",unique,"ADMIN",adminName,"MANAGER",managerName,
                        "DOCTOR","doctor-"+unique,"departmentId",department,"date",day,"year",year,
                        "reportApplicationClock",calendarEnd,"dailyPath",daily,"monthlyPath",monthly)));
        if ("true".equals(System.getenv("MEDIFLOW_FE_REPORT_BROWSER_HOLD"))) {
            Path stopFile = work.resolve("report-browser-stop"); Files.deleteIfExists(stopFile);
            long deadline = System.nanoTime()+Duration.ofMinutes(45).toNanos();
            while (!Files.exists(stopFile) && System.nanoTime()<deadline) Thread.sleep(500);
            assertThat(Files.exists(stopFile)).as("browser acceptance completed before timeout").isTrue();
        }
    }

    private void assertReportPeriod(JsonNode period, java.math.BigDecimal cash, java.math.BigDecimal refunds) {
        assertThat(period.path("completedVisits").asInt()).isZero();
        assertThat(period.path("labTests").asInt()).isZero();
        assertThat(period.path("dispensedPrescriptions").asInt()).isEqualTo(1);
        assertThat(period.path("dispensedUnits").asInt()).isEqualTo(3);
        assertThat(period.path("cashReceived").decimalValue()).isEqualByComparingTo(cash);
        assertThat(period.path("refunds").decimalValue()).isEqualByComparingTo(refunds);
        assertThat(period.path("netCash").decimalValue()).isEqualByComparingTo(cash.subtract(refunds));
    }

    private String refundBody(String key, int amount) throws IOException {
        return JSON.writeValueAsString(Map.of("idempotencyKey", key, "amount", amount,
                "reason", "Isolated cashier recorded refund", "paymentMethod", "CASH"));
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

    private JsonNode payActualExam(String gateway, UUID appointmentId, UUID patientId,
                                   String cashierToken, String nurseToken) throws Exception {
        String discovery = "/api/v1/billing/payment-requests/by-source?purpose=EXAM&sourceId="
                + appointmentId + "&patientId=" + patientId + "&careEpisodeId=" + appointmentId;
        JsonNode[] found = new JsonNode[1];
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(300)).untilAsserted(() -> {
            HttpResponse<String> response = request(gateway, "GET", discovery, cashierToken, null);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            found[0] = data(response);
        });
        JsonNode request = found[0];
        assertThat(request.path("sourceId").asText()).isEqualTo(appointmentId.toString());
        assertThat(request.path("patientId").asText()).isEqualTo(patientId.toString());
        assertThat(request.path("careEpisodeId").asText()).isEqualTo(appointmentId.toString());
        assertThat(request.path("remainingAmount").decimalValue()).isEqualByComparingTo("100000.00");
        assertThat(request.path("paidAmount").decimalValue()).isZero();
        assertThat(request.path("status").asText()).isEqualTo("PENDING");
        assertThat(request(gateway, "GET", discovery, nurseToken, null).statusCode()).isEqualTo(403);
        String paymentPath = "/api/v1/billing/payment-requests/" + request.path("paymentRequestId").asText() + "/payments";
        String body = JSON.writeValueAsString(Map.of("idempotencyKey", "paid-exam-" + appointmentId,
                "amount", request.path("remainingAmount").decimalValue(),
                "currency", request.path("currency").asText(), "paymentMethod", "CASH"));
        assertThat(request(gateway, "POST", paymentPath, nurseToken, body).statusCode()).isEqualTo(403);
        JsonNode payment = data(request(gateway, "POST", paymentPath, cashierToken, body));
        JsonNode replay = data(request(gateway, "POST", paymentPath, cashierToken, body));
        assertThat(payment.path("transactionId").asText()).isNotBlank();
        // PostgreSQL persists microseconds; compare the business response exactly apart from that precision.
        assertThat(Duration.between(Instant.parse(payment.path("completedAt").asText()),
                Instant.parse(replay.path("completedAt").asText())).abs())
                .isLessThan(Duration.ofNanos(1_000));
        JsonNode replayFields = replay.deepCopy();
        JsonNode paymentFields = payment.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) replayFields).remove("completedAt");
        ((com.fasterxml.jackson.databind.node.ObjectNode) paymentFields).remove("completedAt");
        assertThat(replayFields).isEqualTo(paymentFields);
        assertThat(payment.path("paymentRequestId")).isEqualTo(request.path("paymentRequestId"));
        JsonNode paid = data(get(gateway, discovery, cashierToken));
        assertThat(paid.path("status").asText()).isEqualTo("PAID");
        assertThat(paid.path("remainingAmount").decimalValue()).isZero();
        assertThat(paid.path("paidAmount").decimalValue()).isEqualByComparingTo("100000.00");
        return payment;
    }

    private UUID createStaff(String gateway, String token, UUID departmentId, String unique,
                             String jobTitle, String label) throws Exception {
        JsonNode staff = data(post(gateway, "/api/v1/org/staff", token,
                "fullName", "Acceptance " + label + " " + unique,
                "departmentId", departmentId.toString(), "jobTitle", jobTitle,
                "specialization", jobTitle.equals("DOCTOR") ? "General medicine" : null,
                "licenseNumber", jobTitle.equals("DOCTOR") ? "LIC-" + label + "-" + unique : null,
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
        return loginSession(gateway, username).path("accessToken").asText();
    }

    private void createPatientAccount(String gateway, String adminToken, String username, UUID patientId) throws Exception {
        post(gateway, "/api/v1/org/accounts", adminToken,
                "username", username, "password", PASSWORD, "staffId", null,
                "patientId", patientId.toString(), "role", "PATIENT");
    }

    private JsonNode loginSession(String gateway, String username) throws Exception {
        HttpResponse<String> response = request(gateway, "POST", "/api/v1/auth/login", null,
                JSON.writeValueAsString(Map.of("username", username, "password", PASSWORD)));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode session = JSON.readTree(response.body());
        assertThat(session.path("accessToken").asText()).isNotBlank();
        return session;
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
                    retainPublicReceipt(method, path, response);
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
            statement.execute("CREATE DATABASE mediflow_billing");
            for (String store : List.of("pharmacy", "lab", "report", "notification"))
                statement.execute("CREATE DATABASE mediflow_" + store);
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
        ServerSocket reservation = reservedPorts.remove(port);
        if (reservation != null) reservation.close();
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        List<String> command = new ArrayList<>(List.of(java, "-Xmx256m", "-jar", jar.toString(),
                "--management.endpoints.web.exposure.include=health,info",
                "--server.port=" + port,
                "--eureka.instance.hostname=127.0.0.1",
                "--eureka.instance.prefer-ip-address=true",
                "--eureka.instance.ip-address=127.0.0.1",
                "--spring.main.banner-mode=off", "--eureka.client.registry-fetch-interval-seconds=1",
                "--eureka.instance.lease-renewal-interval-in-seconds=2", "--eureka.client.initial-instance-info-replication-interval-seconds=1"));
        if (name.equals("support") && reportCalendarAcceptance()) {
            Path fixtureJar = work.resolve("report-calendar-test-only.jar").toAbsolutePath();
            if (!Files.exists(fixtureJar)) {
                String resource = "com/mediflow/acceptance/ReportCalendarAcceptanceInitializer";
                try (var output = new java.util.jar.JarOutputStream(Files.newOutputStream(fixtureJar))) {
                    for (String suffix : List.of(".class", "$1.class")) {
                        String entry = resource + suffix;
                        output.putNextEntry(new java.util.jar.JarEntry(entry));
                        try (var input = getClass().getClassLoader().getResourceAsStream(entry)) {
                            if (input == null) throw new IOException("Missing test clock class " + entry);
                            input.transferTo(output);
                        }
                        output.closeEntry();
                    }
                }
            }
            command.subList(2, 4).clear();
            command.addAll(2, List.of("-Dloader.path=" + fixtureJar, "-cp", jar.toString(),
                    "org.springframework.boot.loader.launch.PropertiesLauncher"));
            command.add("--context.initializer.classes=com.mediflow.acceptance.ReportCalendarAcceptanceInitializer");
        }
        if (environment.containsKey("EUREKA_DEFAULT_ZONE"))
            command.add("--eureka.client.service-url.defaultZone=" + environment.get("EUREKA_DEFAULT_ZONE"));
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
                .ignoreExceptionsMatching(SixServiceOutpatientAcceptanceIT::isTransientConnectionFailure)
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
                .ignoreExceptionsMatching(SixServiceOutpatientAcceptanceIT::isTransientConnectionFailure)
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

    private Path requiredJar(String module) {
        String directory = module.endsWith("server") || module.equals("gateway") ? module : module + "-service";
        Path jar = Path.of("..", directory, "target", directory + "-0.0.1-SNAPSHOT.jar").toAbsolutePath();
        assertThat(jar).as("Package the current reactor before acceptance: " + module).isRegularFile();
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
