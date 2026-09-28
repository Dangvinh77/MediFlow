package com.mediflow.surgery.infrastructure.client;

import com.mediflow.common.security.JwtClaims;
import com.mediflow.surgery.application.exception.UpstreamUnavailableException;
import com.mediflow.surgery.application.port.out.PatientLookupPort;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PatientLookupHttpTest {

    private static final String SECRET = "surgery-test-secret-at-least-32-bytes";
    private static final AtomicReference<Reply> REPLY = new AtomicReference<>(Reply.EXISTS);
    private static final AtomicReference<String> AUTHORIZATION = new AtomicReference<>();
    private static final AtomicReference<String> CORRELATION = new AtomicReference<>();
    private static HttpServer server;

    @Autowired
    PatientLookupPort patientLookup;

    @DynamicPropertySource
    static void patientService(DynamicPropertyRegistry registry) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/patients/", PatientLookupHttpTest::reply);
        server.start();
        registry.add("mediflow.surgery.patient.base-url",
                () -> "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterAll
    static void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void exists_httpStub_usesCanonicalPathCorrelationAndServiceCredential() {
        UUID patientId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        REPLY.set(Reply.EXISTS);

        assertThat(patientLookup.exists(patientId, correlationId)).isTrue();
        assertThat(CORRELATION.get()).isEqualTo(correlationId.toString());
        String bearer = AUTHORIZATION.get();
        assertThat(bearer).startsWith("Bearer ");
        var claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(bearer.substring(7)).getPayload();
        assertThat(claims.getSubject()).isEqualTo("surgery-service");
        assertThat(claims.get(JwtClaims.TYPE)).isEqualTo(JwtClaims.SERVICE_TOKEN_TYPE);
        assertThat(claims.get(JwtClaims.ROLE)).isEqualTo("SYSTEM");
    }

    @Test
    void exists_httpStub_distinguishesAbsenceFromOutageAndMalformedData() {
        UUID patientId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        REPLY.set(Reply.ABSENT);
        assertThat(patientLookup.exists(patientId, correlationId)).isFalse();
        REPLY.set(Reply.NOT_FOUND);
        assertThat(patientLookup.exists(patientId, correlationId)).isFalse();
        for (Reply bad : new Reply[] {Reply.UNAVAILABLE, Reply.MALFORMED, Reply.WRONG_ID}) {
            REPLY.set(bad);
            assertThatThrownBy(() -> patientLookup.exists(patientId, correlationId))
                    .isInstanceOf(UpstreamUnavailableException.class);
        }
    }

    private static void reply(HttpExchange exchange) throws IOException {
        AUTHORIZATION.set(exchange.getRequestHeaders().getFirst("Authorization"));
        CORRELATION.set(exchange.getRequestHeaders().getFirst(JwtClaims.HEADER_CORRELATION_ID));
        UUID requestedId = UUID.fromString(exchange.getRequestURI().getPath()
                .replace("/api/v1/patients/", "").replace("/exists", ""));
        Reply mode = REPLY.get();
        int status = mode == Reply.NOT_FOUND ? 404 : mode == Reply.UNAVAILABLE ? 503 : 200;
        String id = mode == Reply.WRONG_ID ? UUID.randomUUID().toString() : requestedId.toString();
        String body = switch (mode) {
            case EXISTS -> envelope(true, id);
            case ABSENT -> envelope(false, id);
            case MALFORMED -> "{\"success\":true,\"data\":{\"exists\":null}}";
            case WRONG_ID -> envelope(true, id);
            case NOT_FOUND, UNAVAILABLE -> "{}";
        };
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String envelope(boolean exists, String patientId) {
        return "{\"success\":true,\"data\":{\"exists\":" + exists
                + ",\"patientId\":\"" + patientId + "\"}}";
    }

    private enum Reply { EXISTS, ABSENT, NOT_FOUND, UNAVAILABLE, MALFORMED, WRONG_ID }
}
