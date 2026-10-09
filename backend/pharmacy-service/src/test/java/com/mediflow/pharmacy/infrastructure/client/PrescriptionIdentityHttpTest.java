package com.mediflow.pharmacy.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import com.mediflow.pharmacy.application.port.out.PrescriptionIdentityPort;
import com.mediflow.pharmacy.infrastructure.config.PharmacyIdentityConfiguration;
import com.mediflow.pharmacy.infrastructure.security.JwtProperties;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;

/** Real Feign HTTP; Patient/Staff use producer files, Department reply is explicitly a local contract example. */
@SpringBootTest(classes = PrescriptionIdentityHttpTest.Config.class, properties = {
        "mediflow.features.care-finance-v2=true", "mediflow.pharmacy.identity.enabled=true",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false", "spring.cloud.openfeign.circuitbreaker.enabled=true"
}, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PrescriptionIdentityHttpTest {
    private static final String SECRET = "pharmacy-identity-test-secret-at-least-32-bytes";
    private static final Instant NOW = Instant.parse("2026-10-09T01:00:00Z");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID DOCTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DEPARTMENT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String CORRELATION = "33333333-3333-3333-3333-333333333333";
    private static final String PATIENT_PATH = "/api/v1/patients/" + PATIENT + "/exists";
    private static final String STAFF_PATH = "/api/v1/org/staff/" + DOCTOR + "/lookup";
    private static final String DEPT_PATH = "/api/v1/org/departments/" + DEPARTMENT + "/lookup";
    private static final Map<String, Reply> REPLIES = new ConcurrentHashMap<>();
    private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();
    private static HttpServer server;
    private final ObjectMapper json = new ObjectMapper();
    @Autowired PrescriptionIdentityPort identities;
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, RabbitAutoConfiguration.class})
    @Import(PharmacyIdentityConfiguration.class)
    static class Config {
        @Bean Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        @Bean JwtProperties jwt() { return new JwtProperties(SECRET); }
    }
    @DynamicPropertySource static void endpoints(DynamicPropertyRegistry properties) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/", exchange -> {
            var path = exchange.getRequestURI().getPath(); TOKENS.put(path, exchange.getRequestHeaders().getFirst("Authorization"));
            var reply = REPLIES.get(path);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("X-Correlation-Id", reply.correlation());
            exchange.sendResponseHeaders(reply.status(), reply.body().length);
            try (var output = exchange.getResponseBody()) { output.write(reply.body()); }
        }); server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        properties.add("mediflow.pharmacy.patient.base-url", () -> base);
        properties.add("mediflow.pharmacy.organization.base-url", () -> base);
    }
    @AfterAll static void close() { if (server != null) server.stop(0); }
    @BeforeEach void replies() throws Exception {
        REPLIES.clear(); TOKENS.clear();
        put(PATIENT_PATH, producer("../patient-service/src/test/resources/contracts/patient.lookup.exists.json"));
        put(STAFF_PATH, producer("../organization-service/src/test/resources/contracts/organization.staff.lookup.json"));
        var envelope = json.createObjectNode(); envelope.put("success", true); envelope.putNull("error");
        envelope.put("correlationId", CORRELATION); envelope.put("timestamp", NOW.toString());
        envelope.putObject("data").put("exists", true).put("active", true).put("departmentId", DEPARTMENT.toString())
                .put("departmentName", "Clinical").put("departmentType", "CLINICAL");
        put(DEPT_PATH, envelope);
    }
    @Test void lookup_originalPatientAndStaffBytesUseExactPathsAndShortLivedServiceCredential() {
        var proof = lookup(); proof.requireExact(PATIENT, DOCTOR, DEPARTMENT, NOW);
        assertThat(TOKENS).hasSize(3).containsKeys(PATIENT_PATH, STAFF_PATH, DEPT_PATH);
        assertThat(proof.checkedAt()).isEqualTo(NOW);
        TOKENS.values().forEach(token -> {
            var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                    .clock(() -> Date.from(NOW)).build().parseSignedClaims(token.substring(7)).getPayload();
            assertThat(claims.getSubject()).isEqualTo("pharmacy-service");
            assertThat(claims).containsEntry("type", "service").containsEntry("role", "SYSTEM");
            assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.plusSeconds(60));
            assertThat(claims).doesNotContainKeys("patientId", "staffId", "departmentId");
        });
    }
    @ParameterizedTest @ValueSource(strings = {"patient", "staff", "nurse", "inactive", "foreign", "department"})
    void lookup_confirmedDenialIsNotTransportAbsenceOrPermission(String defect) throws Exception {
        if (defect.equals("patient")) { var root = root(PATIENT_PATH); data(root).put("exists", false); put(PATIENT_PATH, root); }
        else if (defect.equals("department")) { var root = root(DEPT_PATH); data(root).put("active", false); put(DEPT_PATH, root); }
        else { var root = root(STAFF_PATH); var data = data(root);
            switch (defect) {
                case "staff" -> { data.put("exists", false).put("active", false).putNull("jobTitle").putNull("departmentId"); data.putArray("eligibleTeamRoles"); }
                case "nurse" -> data.put("jobTitle", "NURSE"); // A team-role label cannot grant prescribing permission.
                case "inactive" -> { data.put("active", false); data.putArray("eligibleTeamRoles"); }
                case "foreign" -> data.put("departmentId", UUID.randomUUID().toString());
            } put(STAFF_PATH, root);
        }
        var proof = lookup();
        assertThatThrownBy(() -> proof.requireExact(PATIENT, DOCTOR, DEPARTMENT, NOW))
                .isInstanceOf(com.mediflow.common.exception.BusinessRuleException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"wrong-patient", "short-id", "string-bool", "absent-active", "missing-role-array",
            "inactive-roles", "wrong-header", "wrong-correlation", "duplicate", "trailing", "missing-department"})
    void lookup_malformedResponseIsUnavailableWithoutSensitiveCause(String defect) throws Exception {
        String path = defect.equals("wrong-patient") ? PATIENT_PATH : STAFF_PATH;
        var root = root(path); var data = data(root);
        switch (defect) {
            case "wrong-patient" -> data.put("patientId", UUID.randomUUID().toString());
            case "short-id" -> data.put("departmentId", "0-0-0-0-1");
            case "string-bool" -> data.put("active", "true");
            case "absent-active" -> data.put("exists", false);
            case "missing-role-array" -> data.remove("eligibleTeamRoles");
            case "inactive-roles" -> data.put("active", false);
            case "wrong-correlation" -> root.put("correlationId", "different");
            case "missing-department" -> data.remove("departmentId");
        }
        String body = json.writeValueAsString(root);
        if (defect.equals("duplicate")) body = body.replace("\"active\":true", "\"active\":false,\"active\":true");
        if (defect.equals("trailing")) body += " {}";
        REPLIES.put(path, new Reply(200, body.getBytes(StandardCharsets.UTF_8), defect.equals("wrong-header") ? "different" : CORRELATION));
        assertThatThrownBy(this::lookup).isInstanceOf(PharmacyUpstreamUnavailableException.class).hasNoCause();
    }
    @ParameterizedTest @ValueSource(ints = {401, 403, 404, 500, 503})
    void lookup_httpFailureNeverTurnsIntoConfirmedMissing(int status) {
        REPLIES.put(PATIENT_PATH, new Reply(status, "PRIVATE".getBytes(StandardCharsets.UTF_8), CORRELATION));
        assertThatThrownBy(this::lookup).isInstanceOf(PharmacyUpstreamUnavailableException.class).hasNoCause();
    }
    @Test void lookup_ambientTransactionRejectedBeforeAnyNetworkRead() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThatThrownBy(this::lookup).isInstanceOf(IllegalStateException.class); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        assertThat(TOKENS).isEmpty();
    }
    @Test void fallback_neitherInventsIdentityNorLeaksUpstreamCause() {
        assertThatThrownBy(() -> new PharmacyPatientFallbackFactory().create(new RuntimeException("PRIVATE"))
                .exists(PATIENT, "PRIVATE", CORRELATION)).isInstanceOf(PharmacyUpstreamUnavailableException.class).hasNoCause();
        assertThatThrownBy(() -> new PharmacyOrganizationFallbackFactory().create(new RuntimeException("PRIVATE"))
                .staff(DOCTOR, "PRIVATE", CORRELATION)).isInstanceOf(PharmacyUpstreamUnavailableException.class).hasNoCause();
    }
    private PrescriptionIdentityPort.Observation lookup() { return identities.lookup(PATIENT, DOCTOR, DEPARTMENT, CORRELATION); }
    private ObjectNode producer(String path) throws Exception {
        var root = (ObjectNode) json.readTree(Files.readAllBytes(Path.of(path)));
        root.put("correlationId", CORRELATION); return root;
    }
    private ObjectNode root(String path) throws Exception { return (ObjectNode) json.readTree(REPLIES.get(path).body()); }
    private static ObjectNode data(ObjectNode root) { return (ObjectNode) root.get("data"); }
    private void put(String path, ObjectNode root) throws Exception { REPLIES.put(path, new Reply(200, json.writeValueAsBytes(root), CORRELATION)); }
    private record Reply(int status, byte[] body, String correlation) { }
}
