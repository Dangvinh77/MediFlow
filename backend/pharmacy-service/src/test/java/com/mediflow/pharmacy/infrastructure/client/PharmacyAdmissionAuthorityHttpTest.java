package com.mediflow.pharmacy.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import com.mediflow.pharmacy.application.port.out.AdmissionAuthorityPort;
import com.mediflow.pharmacy.infrastructure.config.PharmacyAdmissionAuthorityConfiguration;
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
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import static org.mockito.Mockito.*;

/** Actual Feign HTTP serialization over exact Inpatient fixture bytes, not producer runtime approval. */
@SpringBootTest(classes = PharmacyAdmissionAuthorityHttpTest.LookupConfiguration.class, properties = {
        "mediflow.features.care-finance-v2=true", "mediflow.pharmacy.admission-authority.enabled=true",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.cloud.openfeign.circuitbreaker.enabled=true",
        "spring.cloud.openfeign.client.config.pharmacyInpatientClient.connectTimeout=2000",
        "spring.cloud.openfeign.client.config.pharmacyInpatientClient.readTimeout=3000"
}, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PharmacyAdmissionAuthorityHttpTest {
    private static final Instant NOW = Instant.parse("2026-10-05T02:00:00Z");
    private static final String SECRET = "pharmacy-admission-test-secret-at-least-32-bytes";
    private static final UUID ID = UUID.fromString("05000000-0000-0000-0000-000000000001");
    private static final String CORRELATION = "04000000-0000-0000-0000-000000000001";
    private static final AtomicReference<Reply> REPLY = new AtomicReference<>();
    private static final AtomicReference<String> AUTH = new AtomicReference<>(), PATH = new AtomicReference<>();
    private static HttpServer server;
    @Autowired AdmissionAuthorityPort authority;
    @Autowired Clock clock;
    private final ObjectMapper mapper = new ObjectMapper();

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, RabbitAutoConfiguration.class})
    @Import(PharmacyAdmissionAuthorityConfiguration.class)
    static class LookupConfiguration {
        @Bean Clock clock() { return mock(Clock.class); }
        @Bean JwtProperties properties() { return new JwtProperties(SECRET); }
    }
    @DynamicPropertySource static void endpoint(DynamicPropertyRegistry properties) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/inpatient/", exchange -> {
            AUTH.set(exchange.getRequestHeaders().getFirst("Authorization")); PATH.set(exchange.getRequestURI().getPath());
            var reply = REPLY.get(); exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("X-Correlation-Id", reply.header());
            exchange.sendResponseHeaders(reply.status(), reply.body().length);
            try (var output = exchange.getResponseBody()) { output.write(reply.body()); }
        });
        server.start(); properties.add("mediflow.pharmacy.inpatient.base-url", () -> "http://127.0.0.1:" + server.getAddress().getPort());
    }
    @BeforeEach void prepare() { when(clock.instant()).thenReturn(NOW); AUTH.set(null); PATH.set(null); }
    @AfterAll static void stop() { if (server != null) server.stop(0); }

    @ParameterizedTest @CsvSource({"admission.active.json,true,true", "admission.discharged.json,true,false", "admission.missing.json,false,false"})
    void lookup_actualProducerFixturePreservesExactStateAndServiceIdentity(String file, boolean exists, boolean eligible) throws Exception {
        reply(200, fixture(file)); var result = authority.findAdmission(ID, CORRELATION);
        assertThat(result.admissionId()).isEqualTo(ID); assertThat(result.exists()).isEqualTo(exists);
        assertThat(result.eligible()).isEqualTo(eligible); assertThat(result.observedAt()).isEqualTo(NOW);
        assertThat(PATH.get()).isEqualTo("/api/v1/inpatient/admissions/" + ID + "/lookup");
        var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .clock(() -> Date.from(NOW)).build().parseSignedClaims(AUTH.get().substring(7)).getPayload();
        assertThat(claims.getSubject()).isEqualTo("pharmacy-service");
        assertThat(claims).containsEntry("type", "service").containsEntry("role", "SYSTEM");
        assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.plusSeconds(60));
        assertThat(claims).doesNotContainKeys("staffId", "patientId", "departmentId");
    }

    @ParameterizedTest @ValueSource(strings = {"admissionId", "patientId", "status", "eligible", "revision", "stale", "future", "correlation", "header", "duplicate", "trailing", "numericRevision"})
    void lookup_invalidOrStaleAuthorityFailsClosed(String defect) throws Exception {
        var root = (ObjectNode) mapper.readTree(fixture("admission.active.json"));
        var data = (ObjectNode) root.get("data");
        switch (defect) {
            case "admissionId" -> data.put("admissionId", UUID.randomUUID().toString());
            case "patientId" -> data.put("patientId", "0-0-0-0-1");
            case "status" -> data.put("status", "UNKNOWN");
            case "eligible" -> data.put("eligible", "true");
            case "revision" -> data.put("sourceRevision", "01");
            case "numericRevision" -> data.put("sourceRevision", 1);
            case "stale" -> data.put("observedAt", NOW.minusSeconds(31).toString());
            case "future" -> data.put("observedAt", NOW.plusSeconds(6).toString());
            case "correlation" -> root.put("correlationId", "different");
        }
        String body = mapper.writeValueAsString(root);
        if (defect.equals("trailing")) body += " {}";
        if (defect.equals("duplicate")) body = body.replace("\"eligible\":true", "\"eligible\":false,\"eligible\":true");
        REPLY.set(new Reply(200, body.getBytes(StandardCharsets.UTF_8), defect.equals("header") ? "different" : CORRELATION));
        assertThatThrownBy(() -> authority.findAdmission(ID, CORRELATION)).isInstanceOf(PharmacyUpstreamUnavailableException.class).hasNoCause();
    }

    @ParameterizedTest @ValueSource(ints = {401, 403, 404, 500, 503})
    void lookup_transportFailureNeverMeansConfirmedAbsence(int status) {
        reply(status, "{}".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> authority.findAdmission(ID, CORRELATION)).isInstanceOf(PharmacyUpstreamUnavailableException.class).hasNoCause();
    }
    @Test void lookup_rowRevisionZeroIsValidWithoutInventingEpisodeOrPlacement() throws Exception {
        var root = (ObjectNode) mapper.readTree(fixture("admission.active.json")); ((ObjectNode) root.get("data")).put("sourceRevision", "0");
        reply(200, mapper.writeValueAsBytes(root)); assertThat(authority.findAdmission(ID, CORRELATION).sourceRevision()).isEqualTo("0");
    }
    @Test void lookup_ambientTransactionRejectsBeforeNetwork() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThatThrownBy(() -> authority.findAdmission(ID, CORRELATION)).isInstanceOf(IllegalStateException.class); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        assertThat(AUTH.get()).isNull();
    }
    @Test void fallback_unavailableHasNoCauseOrFabricatedObservation() {
        var client = new PharmacyAdmissionFallbackFactory().create(new RuntimeException("token secret"));
        assertThatThrownBy(() -> client.lookup(ID, "private", CORRELATION)).isInstanceOf(PharmacyUpstreamUnavailableException.class).hasNoCause();
    }
    private static void reply(int status, byte[] body) { REPLY.set(new Reply(status, body, CORRELATION)); }
    private static byte[] fixture(String name) throws Exception { return Files.readAllBytes(Path.of("../inpatient-service/src/test/resources/contracts/admission-authority-v1/" + name)); }
    private record Reply(int status, byte[] body, String header) {}
}
