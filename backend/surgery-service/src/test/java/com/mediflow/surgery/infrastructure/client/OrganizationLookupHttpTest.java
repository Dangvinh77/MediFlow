package com.mediflow.surgery.infrastructure.client;

import com.mediflow.common.security.JwtClaims;
import com.mediflow.surgery.application.exception.UpstreamUnavailableException;
import com.mediflow.surgery.application.port.out.OrganizationLookupPort;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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
class OrganizationLookupHttpTest {

    private static final String SECRET = "surgery-test-secret-at-least-32-bytes";
    private static final UUID STAFF_DEPARTMENT_ID = UUID.fromString("a60e900c-4100-43a1-857b-3a2e9db37a8a");
    private static final AtomicReference<Reply> STAFF_REPLY = new AtomicReference<>(Reply.ACTIVE);
    private static final AtomicReference<Reply> DEPARTMENT_REPLY = new AtomicReference<>(Reply.ACTIVE);
    private static final AtomicReference<String> PATH = new AtomicReference<>();
    private static final AtomicReference<String> AUTHORIZATION = new AtomicReference<>();
    private static final AtomicReference<String> CORRELATION = new AtomicReference<>();
    private static HttpServer server;

    @Autowired
    OrganizationLookupPort lookups;

    @DynamicPropertySource
    static void organizationService(DynamicPropertyRegistry registry) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/org/staff/", exchange -> reply(exchange, STAFF_REPLY.get(), true));
        server.createContext("/api/v1/org/departments/", exchange -> reply(exchange, DEPARTMENT_REPLY.get(), false));
        server.start();
        registry.add("mediflow.surgery.organization.base-url",
                () -> "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @BeforeEach
    void resetReplies() {
        STAFF_REPLY.set(Reply.ACTIVE);
        DEPARTMENT_REPLY.set(Reply.ACTIVE);
    }

    @AfterAll
    static void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void findStaff_activeProjection_usesLockedPathServiceTokenAndCorrelation() {
        UUID staffId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        var snapshot = lookups.findStaff(staffId, correlationId.toString());

        assertThat(snapshot.kind()).isEqualTo(OrganizationLookupPort.ReferenceKind.STAFF);
        assertThat(snapshot.referenceId()).isEqualTo(staffId);
        assertThat(snapshot.state()).isEqualTo(OrganizationLookupPort.ReferenceState.ACTIVE);
        assertThat(snapshot.jobTitleCode()).isEqualTo("NURSE");
        assertThat(snapshot.staffDepartmentId()).isEqualTo(STAFF_DEPARTMENT_ID);
        assertThat(snapshot.sourceRevision()).isNull();
        assertThat(snapshot.observedAt()).isNotNull();
        assertThat(PATH.get()).isEqualTo("/api/v1/org/staff/" + staffId + "/lookup");
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
    void findStaff_inactiveAndMissing_areDifferentFromUpstreamFailure() {
        UUID staffId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();

        STAFF_REPLY.set(Reply.INACTIVE);
        assertThat(lookups.findStaff(staffId, correlationId).state())
                .isEqualTo(OrganizationLookupPort.ReferenceState.INACTIVE);
        STAFF_REPLY.set(Reply.ABSENT);
        assertThat(lookups.findStaff(staffId, correlationId).state())
                .isEqualTo(OrganizationLookupPort.ReferenceState.NOT_FOUND);
        STAFF_REPLY.set(Reply.NOT_FOUND);
        assertThat(lookups.findStaff(staffId, correlationId).state())
                .isEqualTo(OrganizationLookupPort.ReferenceState.NOT_FOUND);
        for (Reply bad : new Reply[] {
                Reply.UNAVAILABLE, Reply.MALFORMED, Reply.WRONG_CORRELATION, Reply.WRONG_HEADER}) {
            STAFF_REPLY.set(bad);
            assertThatThrownBy(() -> lookups.findStaff(staffId, correlationId))
                    .isInstanceOf(UpstreamUnavailableException.class);
        }
    }

    @Test
    void findDepartment_validatesEchoAndDistinguishesActiveInactiveAndAbsence() {
        UUID departmentId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();

        var active = lookups.findDepartment(departmentId, correlationId);
        assertThat(active.kind()).isEqualTo(OrganizationLookupPort.ReferenceKind.DEPARTMENT);
        assertThat(active.referenceId()).isEqualTo(departmentId);
        assertThat(active.state()).isEqualTo(OrganizationLookupPort.ReferenceState.ACTIVE);
        assertThat(PATH.get()).isEqualTo("/api/v1/org/departments/" + departmentId + "/lookup");
        assertThat(CORRELATION.get()).isEqualTo(correlationId);

        DEPARTMENT_REPLY.set(Reply.INACTIVE);
        assertThat(lookups.findDepartment(departmentId, correlationId).state())
                .isEqualTo(OrganizationLookupPort.ReferenceState.INACTIVE);
        DEPARTMENT_REPLY.set(Reply.ABSENT);
        assertThat(lookups.findDepartment(departmentId, correlationId).state())
                .isEqualTo(OrganizationLookupPort.ReferenceState.NOT_FOUND);
        DEPARTMENT_REPLY.set(Reply.NOT_FOUND);
        assertThat(lookups.findDepartment(departmentId, correlationId).state())
                .isEqualTo(OrganizationLookupPort.ReferenceState.NOT_FOUND);
        for (Reply bad : new Reply[] {
                Reply.UNAVAILABLE, Reply.MALFORMED, Reply.WRONG_ID,
                Reply.WRONG_CORRELATION, Reply.WRONG_HEADER}) {
            DEPARTMENT_REPLY.set(bad);
            assertThatThrownBy(() -> lookups.findDepartment(departmentId, correlationId))
                    .isInstanceOf(UpstreamUnavailableException.class);
        }
    }

    @Test
    void findRoom_withoutDeployedProducerEndpoint_failsClosedInsteadOfConfirmedAbsence() {
        assertThatThrownBy(() -> lookups.findRoom(UUID.randomUUID(), UUID.randomUUID().toString()))
                .isInstanceOf(UpstreamUnavailableException.class)
                .hasMessageContaining("room lookup is unavailable");
    }

    private static void reply(HttpExchange exchange, Reply mode, boolean staff) throws IOException {
        String path = exchange.getRequestURI().getPath();
        PATH.set(path);
        AUTHORIZATION.set(exchange.getRequestHeaders().getFirst("Authorization"));
        String correlationId = exchange.getRequestHeaders().getFirst(JwtClaims.HEADER_CORRELATION_ID);
        CORRELATION.set(correlationId);
        String prefix = staff ? "/api/v1/org/staff/" : "/api/v1/org/departments/";
        UUID requestedId = UUID.fromString(path.substring(prefix.length(), path.length() - "/lookup".length()));
        int status = mode == Reply.NOT_FOUND ? 404 : mode == Reply.UNAVAILABLE ? 503 : 200;
        String echoedId = mode == Reply.WRONG_ID ? UUID.randomUUID().toString() : requestedId.toString();
        String body = switch (mode) {
            case ACTIVE, INACTIVE, WRONG_HEADER -> envelope(staff
                    ? "{\"exists\":true,\"active\":" + (mode == Reply.ACTIVE)
                    + ",\"jobTitle\":\"NURSE\",\"departmentId\":\"" + STAFF_DEPARTMENT_ID + "\"}"
                    : "{\"exists\":true,\"active\":" + (mode == Reply.ACTIVE)
                    + ",\"departmentId\":\"" + echoedId
                    + "\",\"departmentName\":\"Surgery\",\"departmentType\":\"CLINICAL\"}",
                    correlationId);
            case ABSENT -> envelope(staff
                    ? "{\"exists\":false,\"active\":false}"
                    : "{\"exists\":false,\"active\":false,\"departmentId\":\""
                    + requestedId + "\"}", correlationId);
            case MALFORMED -> envelope("{\"exists\":true,\"active\":true}", correlationId);
            case WRONG_ID -> envelope("{\"exists\":true,\"active\":true,\"departmentId\":\""
                    + echoedId + "\",\"departmentName\":\"Surgery\",\"departmentType\":\"CLINICAL\"}",
                    correlationId);
            case WRONG_CORRELATION -> envelope(staff
                    ? "{\"exists\":false,\"active\":false}"
                    : "{\"exists\":false,\"active\":false,\"departmentId\":\""
                    + requestedId + "\"}", UUID.randomUUID().toString());
            case NOT_FOUND, UNAVAILABLE -> "{}";
        };
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set(JwtClaims.HEADER_CORRELATION_ID,
                mode == Reply.WRONG_HEADER ? UUID.randomUUID().toString() : correlationId);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String envelope(String data, String correlationId) {
        return "{\"success\":true,\"data\":" + data
                + ",\"error\":null,\"correlationId\":\"" + correlationId + "\"}";
    }

    private enum Reply {
        ACTIVE, INACTIVE, ABSENT, NOT_FOUND, UNAVAILABLE, MALFORMED, WRONG_ID,
        WRONG_CORRELATION, WRONG_HEADER
    }
}
