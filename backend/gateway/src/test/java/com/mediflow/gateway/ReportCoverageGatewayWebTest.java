package com.mediflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.common.security.Roles;
import com.mediflow.gateway.auth.OrganizationAuthClient;
import com.mediflow.gateway.security.JwtTokenService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Real Gateway HTTP/JWT/discovery against a synthetic Support HTTP peer, not coverage acceptance. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes",
        "eureka.client.enabled=false",
        "spring.cloud.gateway.discovery.locator.enabled=false"
})
@AutoConfigureWebTestClient
class ReportCoverageGatewayWebTest {
    private static final String SECRET = "test-secret-must-have-at-least-32-bytes";
    private static final String PREVIEW = "/api/v1/reports/outpatient/coverage/preview";
    private static final String ACCEPT = "/api/v1/reports/outpatient/coverage/acceptances";
    private static final AtomicInteger FORWARDED = new AtomicInteger();
    private static final AtomicReference<Seen> LAST = new AtomicReference<>();
    private static HttpServer support;
    private record Seen(String method, String uri, String body, String bearer, String actor, String role,
                        String correlation) { }

    @Autowired WebTestClient http;
    @Autowired JwtTokenService tokens;
    @MockBean OrganizationAuthClient organization;

    @DynamicPropertySource
    static void discoverSupport(DynamicPropertyRegistry registry) throws IOException {
        support = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        support.createContext("/api/v1/reports/", ReportCoverageGatewayWebTest::respond);
        support.start();
        registry.add("spring.cloud.discovery.client.simple.instances.support-service[0].uri",
                () -> "http://127.0.0.1:" + support.getAddress().getPort());
    }

    @BeforeEach void reset() { FORWARDED.set(0); LAST.set(null); }
    @AfterAll static void stop() { if (support != null) support.stop(0); }

    @Test
    void adminPreviewPreservesQueryJwtIdentityCorrelationAndNoStore() {
        UUID actor = UUID.randomUUID();
        String bearer = "Bearer " + tokens.issueAccessToken(actor, Roles.ADMIN);
        String correlation = UUID.randomUUID().toString();
        String uri = PREVIEW + "?fromDate=2026-10-01&toDate=2026-10-02&departmentId=" + UUID.randomUUID();
        request(HttpMethod.GET, uri, bearer).header("X-Correlation-Id", correlation)
                .header("X-User-Id", UUID.randomUUID().toString()).header("X-User-Role", Roles.MANAGER)
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals("Cache-Control", "no-store")
                .expectHeader().valueEquals("X-Correlation-Id", correlation)
                .expectBody().jsonPath("$.data.syntheticPeer").isEqualTo(true);
        assertThat(FORWARDED.get()).isEqualTo(1);
        assertThat(LAST.get()).isEqualTo(new Seen("GET", uri, "", bearer, actor.toString(), Roles.ADMIN, correlation));
    }

    @Test
    void approvalPreservesBodyAndDownstreamConflictRatherThanSynthesizingSuccess() {
        UUID actor = UUID.randomUUID();
        String bearer = "Bearer " + tokens.issueAccessToken(actor, Roles.ADMIN);
        String correlation = UUID.randomUUID().toString();
        // Deliberately not a valid approval: Gateway transports; Report validates proof and idempotency.
        String body = "{\"requestId\":\"" + UUID.randomUUID() + "\",\"expectedFingerprint\":\"synthetic\"}";
        request(HttpMethod.POST, ACCEPT, bearer).header("X-Correlation-Id", correlation)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange()
                .expectStatus().isEqualTo(409)
                .expectHeader().valueEquals("X-Correlation-Id", correlation)
                .expectBody().jsonPath("$.error.code").isEqualTo("REPORT_COVERAGE_CONFLICT");
        assertThat(FORWARDED.get()).isEqualTo(1);
        assertThat(LAST.get()).isEqualTo(new Seen("POST", ACCEPT, body, bearer, actor.toString(), Roles.ADMIN, correlation));
    }

    @Test
    void otherRolesWrongMethodsAndNestedPathsNeverReachSupport() {
        for (String role : List.of(Roles.MANAGER, Roles.DOCTOR, Roles.NURSE, Roles.PHARMACIST,
                Roles.LAB_TECH, Roles.CASHIER, Roles.PATIENT)) {
            String bearer = "Bearer " + tokens.issueAccessToken(UUID.randomUUID(), role,
                    null, null, Roles.PATIENT.equals(role) ? UUID.randomUUID() : null);
            request(HttpMethod.GET, PREVIEW, bearer).header("X-User-Role", Roles.ADMIN)
                    .exchange().expectStatus().isForbidden();
            request(HttpMethod.POST, ACCEPT, bearer).exchange().expectStatus().isForbidden();
        }
        String admin = "Bearer " + tokens.issueAccessToken(UUID.randomUUID(), Roles.ADMIN);
        for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
                HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.HEAD)) {
            if (method != HttpMethod.GET) request(method, PREVIEW, admin).exchange().expectStatus().isForbidden();
            if (method != HttpMethod.POST) request(method, ACCEPT, admin).exchange().expectStatus().isForbidden();
        }
        for (String suffix : List.of("/extra", "/", "s")) {
            request(HttpMethod.GET, PREVIEW + suffix, admin).exchange().expectStatus().isForbidden();
            request(HttpMethod.POST, ACCEPT + suffix, admin).exchange().expectStatus().isForbidden();
        }
        assertThat(FORWARDED.get()).isZero();
    }

    @Test
    void anonymousInvalidExpiredMissingExpirationAndNonHumanTokensNeverReachSupport() {
        var key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String noExpiration = Jwts.builder().subject(UUID.randomUUID().toString())
                .claim("role", Roles.ADMIN).claim("type", "access").signWith(key).compact();
        String expired = Jwts.builder().subject(UUID.randomUUID().toString())
                .claim("role", Roles.ADMIN).claim("type", "access")
                .expiration(Date.from(Instant.now().minusSeconds(120))).signWith(key).compact();
        for (String path : List.of(PREVIEW, ACCEPT)) {
            HttpMethod method = path.equals(PREVIEW) ? HttpMethod.GET : HttpMethod.POST;
            http.method(method).uri(path).exchange().expectStatus().isUnauthorized();
            for (String token : List.of(noExpiration, expired, "invalid.jwt.signature",
                    tokens.issueRefreshToken(UUID.randomUUID(), Roles.ADMIN))) {
                request(method, path, "Bearer " + token).exchange().expectStatus().isUnauthorized();
            }
            request(method, path, "Bearer " + tokens.issueServiceToken(
                    "report-service", Roles.SYSTEM, UUID.randomUUID().toString()))
                    .exchange().expectStatus().isForbidden();
        }
        assertThat(FORWARDED.get()).isZero();
    }

    private WebTestClient.RequestBodySpec request(HttpMethod method, String uri, String bearer) {
        return http.method(method).uri(uri).header("Authorization", bearer);
    }

    private static void respond(HttpExchange request) throws IOException {
        FORWARDED.incrementAndGet();
        String correlation = request.getRequestHeaders().getFirst("X-Correlation-Id");
        LAST.set(new Seen(request.getRequestMethod(), request.getRequestURI().toString(),
                new String(request.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                request.getRequestHeaders().getFirst("Authorization"),
                request.getRequestHeaders().getFirst("X-User-Id"),
                request.getRequestHeaders().getFirst("X-User-Role"), correlation));
        boolean preview = request.getRequestMethod().equals("GET");
        byte[] response = (preview ? "{\"success\":true,\"data\":{\"syntheticPeer\":true}}"
                : "{\"success\":false,\"error\":{\"code\":\"REPORT_COVERAGE_CONFLICT\"}}")
                .getBytes(StandardCharsets.UTF_8);
        request.getResponseHeaders().set("Content-Type", "application/json");
        request.getResponseHeaders().set("Cache-Control", "no-store");
        request.sendResponseHeaders(preview ? 200 : 409, response.length);
        try (var output = request.getResponseBody()) { output.write(response); }
    }
}
