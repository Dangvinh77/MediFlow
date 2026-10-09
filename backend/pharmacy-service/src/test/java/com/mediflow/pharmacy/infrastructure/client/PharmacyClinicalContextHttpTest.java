package com.mediflow.pharmacy.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import com.mediflow.pharmacy.application.port.out.OutpatientPrescriptionContextPort;
import com.mediflow.pharmacy.application.port.in.CreateCarePrescriptionWithContextUseCase;
import com.mediflow.pharmacy.infrastructure.config.PharmacyClinicalContextConfiguration;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
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

/** Real Feign HTTP over original Clinical fixtures, not a substitute for a multi-JVM care workflow. */
@SpringBootTest(classes = PharmacyClinicalContextHttpTest.Config.class, properties = {
        "mediflow.features.care-finance-v2=true", "mediflow.pharmacy.clinical-context.enabled=true",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.cloud.openfeign.circuitbreaker.enabled=true"
}, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PharmacyClinicalContextHttpTest {
    private static final String SECRET = "pharmacy-context-test-secret-at-least-32-bytes";
    private static final Instant NOW = Instant.parse("2026-10-09T01:00:00Z");
    private static final UUID ID = UUID.fromString("09000000-0000-0000-0000-000000000001");
    private static final String CORRELATION = "09000000-0000-0000-0000-000000000006";
    private static final AtomicReference<Reply> REPLY = new AtomicReference<>();
    private static final AtomicReference<String> AUTH = new AtomicReference<>(), PATH = new AtomicReference<>(), CORR = new AtomicReference<>();
    private static HttpServer server;
    @Autowired OutpatientPrescriptionContextPort contexts;
    private final ObjectMapper mapper = new ObjectMapper();
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, RabbitAutoConfiguration.class})
    @Import(PharmacyClinicalContextConfiguration.class)
    static class Config {
        @Bean Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        @Bean JwtProperties jwt() { return new JwtProperties(SECRET); }
        @Bean CreateCarePrescriptionWithContextUseCase writer() { return org.mockito.Mockito.mock(CreateCarePrescriptionWithContextUseCase.class); }
        @Bean com.mediflow.pharmacy.application.port.out.PrescriptionIdentityPort identities() {
            return org.mockito.Mockito.mock(com.mediflow.pharmacy.application.port.out.PrescriptionIdentityPort.class);
        }
    }
    @DynamicPropertySource static void endpoint(DynamicPropertyRegistry properties) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/records/", exchange -> {
            AUTH.set(exchange.getRequestHeaders().getFirst("Authorization")); PATH.set(exchange.getRequestURI().getPath());
            CORR.set(exchange.getRequestHeaders().getFirst("X-Correlation-Id"));
            var reply = REPLY.get(); exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("X-Correlation-Id", reply.header());
            exchange.sendResponseHeaders(reply.status(), reply.body().length);
            try (var output = exchange.getResponseBody()) { output.write(reply.body()); }
        });
        server.start(); properties.add("mediflow.pharmacy.clinical.base-url", () -> "http://127.0.0.1:" + server.getAddress().getPort());
    }
    @BeforeEach void prepare() { AUTH.set(null); PATH.set(null); CORR.set(null); }
    @AfterAll static void stop() { if (server != null) server.stop(0); }

    @ParameterizedTest @ValueSource(strings = {"appointment.json", "walk-in.json", "missing.json"})
    void lookup_sameProducerBytesPreserveClinicalEpisodeAndServiceIdentity(String file) throws Exception {
        var fixture = fixture(file); reply(200, fixture, CORRELATION);
        var actual = contexts.findRecord(ID, CORRELATION);
        var expected = mapper.readTree(fixture).get("data");
        assertThat(actual.exists()).isEqualTo(expected.get("exists").booleanValue());
        assertThat(actual.recordId()).isEqualTo(ID); assertThat(actual.observedAt()).isEqualTo(NOW);
        if (actual.exists()) {
            assertThat(actual.careEpisodeId().toString()).isEqualTo(expected.get("careEpisodeId").textValue());
            assertThat(actual.doctorId().toString()).isEqualTo(expected.get("doctorId").textValue());
            actual.requireExact(ID, actual.patientId(), actual.doctorId(), actual.departmentId(), actual.careEpisodeId(), NOW);
        } else assertThat(actual.careEpisodeId()).isNull();
        assertThat(PATH.get()).isEqualTo("/api/v1/records/" + ID + "/prescription-context");
        assertThat(CORR.get()).isEqualTo(CORRELATION);
        var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .clock(() -> Date.from(NOW)).build().parseSignedClaims(AUTH.get().substring(7)).getPayload();
        assertThat(claims.getSubject()).isEqualTo("pharmacy-service");
        assertThat(claims).containsEntry("type", "service").containsEntry("role", "SYSTEM");
        assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.plusSeconds(60));
        assertThat(claims).doesNotContainKeys("patientId", "staffId", "departmentId");
    }
    @ParameterizedTest @ValueSource(strings = {"recordId", "doctorId", "patientId", "departmentId", "careEpisodeId", "careEpisodeType",
            "recordStatus", "disposition", "exists", "stale", "future", "correlation", "header", "trailing", "duplicate"})
    void lookup_malformedOrStaleSourceFailsUnavailableWithoutSensitiveCause(String defect) throws Exception {
        var root = (ObjectNode) mapper.readTree(fixture("appointment.json")); var data = (ObjectNode) root.get("data");
        switch (defect) {
            case "recordId" -> data.put("recordId", UUID.randomUUID().toString());
            case "doctorId", "patientId", "departmentId", "careEpisodeId" -> data.put(defect, "0-0-0-0-1");
            case "careEpisodeType" -> data.put("careEpisodeType", "ADMISSION");
            case "recordStatus" -> data.put("recordStatus", "UNKNOWN");
            case "disposition" -> data.put("disposition", "UNKNOWN");
            case "exists" -> data.put("exists", "true");
            case "stale" -> data.put("observedAt", NOW.minusSeconds(31).toString());
            case "future" -> data.put("observedAt", NOW.plusSeconds(6).toString());
            case "correlation" -> root.put("correlationId", "different");
        }
        String body = mapper.writeValueAsString(root);
        if (defect.equals("trailing")) body += " {}";
        if (defect.equals("duplicate")) body = body.replace("\"exists\":true", "\"exists\":false,\"exists\":true");
        reply(200, body.getBytes(StandardCharsets.UTF_8), defect.equals("header") ? "different" : CORRELATION);
        assertThatThrownBy(() -> contexts.findRecord(ID, CORRELATION)).isInstanceOf(PharmacyUpstreamUnavailableException.class).hasNoCause();
    }
    @ParameterizedTest @ValueSource(ints = {401, 403, 404, 500, 503})
    void lookup_transportFailureIsNotAbsence(int status) {
        reply(status, "PRIVATE".getBytes(StandardCharsets.UTF_8), CORRELATION);
        assertThatThrownBy(() -> contexts.findRecord(ID, CORRELATION)).isInstanceOf(PharmacyUpstreamUnavailableException.class).hasNoCause();
    }
    @Test void lookup_missingSourceCannotCarryBusinessIdentity() throws Exception {
        var root = (ObjectNode) mapper.readTree(fixture("missing.json")); ((ObjectNode) root.get("data")).put("doctorId", UUID.randomUUID().toString());
        reply(200, mapper.writeValueAsBytes(root), CORRELATION);
        assertThatThrownBy(() -> contexts.findRecord(ID, CORRELATION)).isInstanceOf(PharmacyUpstreamUnavailableException.class);
    }
    @Test void lookup_ambientTransactionRejectsBeforeNetwork() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThatThrownBy(() -> contexts.findRecord(ID, CORRELATION)).isInstanceOf(IllegalStateException.class); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        assertThat(AUTH.get()).isNull();
    }
    @Test void fallback_doesNotLeakCauseOrInventSource() {
        var client = new PharmacyClinicalContextFallbackFactory().create(new RuntimeException("PRIVATE"));
        assertThatThrownBy(() -> client.lookup(ID, "PRIVATE", CORRELATION)).isInstanceOf(PharmacyUpstreamUnavailableException.class).hasNoCause();
    }
    private static byte[] fixture(String name) throws Exception { return Files.readAllBytes(Path.of("../clinical-service/src/test/resources/contracts/prescription-context-v1/" + name)); }
    private static void reply(int status, byte[] body, String header) { REPLY.set(new Reply(status, body, header)); }
    private record Reply(int status, byte[] body, String header) {}
}
