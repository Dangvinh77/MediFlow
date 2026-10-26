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
import java.net.URI;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Routing/security evidence only. The downstream double does not prove actual Billing acceptance. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes",
        "eureka.client.enabled=false",
        "spring.cloud.gateway.discovery.locator.enabled=false"
})
@AutoConfigureWebTestClient
class BillingDiscoveryGatewayWebTest {
    // Route/forwarding contract fixture. AccountSessionValidationWebTest exercises the real guard.
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.mediflow.gateway.filter.AccountSessionValidationFilter sessionValidation;

    @org.junit.jupiter.api.BeforeEach
    void isolateAccountStateDependency() {
        org.mockito.Mockito.when(sessionValidation.filter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> ((org.springframework.cloud.gateway.filter.GatewayFilterChain) call.getArgument(1))
                        .filter(call.getArgument(0)));
    }

    private static final String PATH = "/api/v1/billing/payment-requests/by-source";
    private static final String SECRET = "test-secret-must-have-at-least-32-bytes";
    private static final String QUERY = "purpose=LAB_TEST&sourceId=00000000-0000-4000-8000-000000000002"
            + "&patientId=00000000-0000-4000-8000-000000000003"
            + "&careEpisodeId=00000000-0000-4000-8000-000000000006";
    private static final AtomicInteger FORWARDED = new AtomicInteger();
    private static final AtomicReference<Captured> CAPTURED = new AtomicReference<>();
    private static final AtomicInteger STATUS = new AtomicInteger(200);
    private static final AtomicReference<String> RESPONSE = new AtomicReference<>();
    private static HttpServer billing;

    private record Captured(String method, String uri, String authorization, String account,
                            String role, String correlation, String body) { }

    @Autowired WebTestClient http;
    @Autowired JwtTokenService tokens;
    @MockBean OrganizationAuthClient organization;

    @DynamicPropertySource
    static void discoverBilling(DynamicPropertyRegistry registry) throws IOException {
        billing = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        billing.createContext("/", BillingDiscoveryGatewayWebTest::respond);
        billing.start();
        registry.add("spring.cloud.discovery.client.simple.instances.billing-service[0].uri",
                () -> "http://127.0.0.1:" + billing.getAddress().getPort());
    }

    @BeforeEach
    void reset() {
        FORWARDED.set(0);
        CAPTURED.set(null);
        STATUS.set(200);
        RESPONSE.set("{\"success\":true,\"data\":{\"opaqueBillingField\":\"unchanged\"}}");
    }

    @AfterAll
    static void stop() {
        if (billing != null) billing.stop(0);
    }

    @ParameterizedTest
    @CsvSource({"ADMIN,EXAM", "CASHIER,LAB_TEST", "ADMIN,PRESCRIPTION"})
    void humanAccess_forwardsOriginalQueryCorrelationAndSignedIdentity(String role, String purpose) {
        UUID account = UUID.randomUUID();
        String token = tokens.issueAccessToken(account, role);
        String correlation = UUID.randomUUID().toString();
        String uri = PATH + "?" + QUERY.replace("LAB_TEST", purpose);

        http.get().uri(URI.create(uri)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-Correlation-Id", correlation).header("X-User-Role", Roles.PATIENT)
                .header("X-User-Id", UUID.randomUUID().toString())
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", correlation)
                .expectBody(String.class).isEqualTo(RESPONSE.get());

        assertThat(FORWARDED.get()).isEqualTo(1);
        assertThat(CAPTURED.get()).isEqualTo(new Captured("GET", uri, "Bearer " + token,
                account.toString(), role, correlation, ""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PATIENT", "SYSTEM", "DOCTOR", "NURSE", "PHARMACIST", "LAB_TECH", "MANAGER"})
    void forbiddenRoles_neverReachBilling(String role) {
        String token = tokens.issueAccessToken(UUID.randomUUID(), role, null, null,
                Roles.PATIENT.equals(role) ? UUID.randomUUID() : null);
        denied(HttpMethod.GET, PATH, token, Roles.SYSTEM.equals(role) ? 401 : 403);
    }

    @Test
    void missingMalformedWrongSignatureAndExpiredTokens_neverReachBilling() {
        denied(HttpMethod.GET, PATH, null, 401);
        denied(HttpMethod.GET, PATH, "not-a-jwt", 401);
        denied(HttpMethod.GET, PATH, signed("another-secret-with-at-least-32-bytes", Instant.now().plusSeconds(60)), 401);
        denied(HttpMethod.GET, PATH, signed(SECRET, Instant.now().minusSeconds(60)), 401);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "CASHIER"})
    void serviceAndRefreshCannotSubstituteForHumanAccess(String role) {
        denied(HttpMethod.GET, PATH, tokens.issueRefreshToken(UUID.randomUUID(), role), 401);
        denied(HttpMethod.GET, PATH, tokens.issueServiceToken("billing-service", role, UUID.randomUUID().toString()), 401);
        denied(HttpMethod.GET, PATH, tokens.issueServiceToken("billing-service", Roles.SYSTEM, UUID.randomUUID().toString()), 403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "CASHIER"})
    void wrongMethodsAndExtraOrNestedPaths_neverReachBilling(String role) {
        String token = tokens.issueAccessToken(UUID.randomUUID(), role);
        for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH,
                HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.OPTIONS)) {
            denied(method, PATH, token, 403);
        }
        for (String path : List.of(PATH + "/extra", PATH + "/", PATH + "-extra",
                "/api/v1/billing/payment-requests/nested/by-source",
                "/api/v1/billing/payment-requests", "/api/v1/billing/payment-requests/" + UUID.randomUUID())) {
            denied(HttpMethod.GET, path, token, 403);
        }
    }

    @ParameterizedTest
    @CsvSource({"400,INVALID_REQUEST", "404,BILLING_PAYMENT_REQUEST_NOT_FOUND", "503,BILLING_LEDGER_UNAVAILABLE"})
    void billingErrors_preserveStatusExactBodyAndCorrelation(int status, String code) {
        String correlation = UUID.randomUUID().toString();
        STATUS.set(status);
        RESPONSE.set("{\"success\":false,\"data\":null,\"error\":{\"code\":\"" + code
                + "\",\"message\":\"Billing detail\",\"details\":[]},\"correlationId\":\"" + correlation + "\"}");
        String uri = PATH + "?" + QUERY;
        http.get().uri(URI.create(uri)).header(HttpHeaders.AUTHORIZATION, "Bearer "
                        + tokens.issueAccessToken(UUID.randomUUID(), Roles.CASHIER))
                .header("X-Correlation-Id", correlation).exchange().expectStatus().isEqualTo(status)
                .expectHeader().valueEquals("X-Correlation-Id", correlation)
                .expectBody(String.class).isEqualTo(RESPONSE.get());
        assertThat(FORWARDED.get()).isEqualTo(1);
        assertThat(CAPTURED.get().uri()).isEqualTo(uri);
        assertThat(CAPTURED.get().correlation()).isEqualTo(correlation);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "purpose=EXAM&purpose=LAB_TEST&sourceId=bad%2Bvalue&patientId=&careEpisodeId=bad&unknown=a%20b"})
    void queryValidationBelongsToBilling_evenMalformedRepeatedAndUnknownValuesAreForwarded(String query) {
        STATUS.set(400);
        RESPONSE.set("{\"success\":false,\"error\":{\"code\":\"INVALID_REQUEST\"}}");
        String uri = PATH + (query.isEmpty() ? "" : "?" + query);
        http.get().uri(URI.create(uri)).header(HttpHeaders.AUTHORIZATION, "Bearer "
                        + tokens.issueAccessToken(UUID.randomUUID(), Roles.ADMIN))
                .exchange().expectStatus().isBadRequest().expectBody(String.class).isEqualTo(RESPONSE.get());
        assertThat(FORWARDED.get()).isEqualTo(1);
        assertThat(CAPTURED.get().uri()).isEqualTo(uri);
        assertThat(UUID.fromString(CAPTURED.get().correlation())).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "CASHIER"})
    void existingPaymentPost_remainsAllowedWithOriginalBody(String role) {
        String path = "/api/v1/billing/payment-requests/" + UUID.randomUUID() + "/payments";
        String body = "{\"idempotencyKey\":\"original-key\",\"amount\":125.50,"
                + "\"currency\":\"VND\",\"paymentMethod\":\"CASH\"}";
        http.post().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer "
                        + tokens.issueAccessToken(UUID.randomUUID(), role))
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                .exchange().expectStatus().isOk();
        assertThat(FORWARDED.get()).isEqualTo(1);
        assertThat(CAPTURED.get().method()).isEqualTo("POST");
        assertThat(CAPTURED.get().uri()).isEqualTo(path);
        assertThat(CAPTURED.get().body()).isEqualTo(body);
    }

    private void denied(HttpMethod method, String path, String token, int status) {
        String correlation = UUID.randomUUID().toString();
        var request = http.method(method).uri(path).header("X-Correlation-Id", correlation)
                .header("X-User-Role", Roles.ADMIN);
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        var response = request.exchange().expectStatus().isEqualTo(status)
                .expectHeader().valueEquals("X-Correlation-Id", correlation);
        if (HttpMethod.HEAD.equals(method)) {
            response.expectBody().isEmpty();
        } else {
            response.expectBody().jsonPath("$.error.code").isEqualTo(status == 401 ? "AUTH_UNAUTHORIZED" : "AUTH_FORBIDDEN")
                    .jsonPath("$.correlationId").isEqualTo(correlation);
        }
        assertThat(FORWARDED.get()).isZero();
    }

    private static String signed(String secret, Instant expiration) {
        return Jwts.builder().subject(UUID.randomUUID().toString()).claim("role", Roles.ADMIN)
                .claim("type", "access").expiration(Date.from(expiration))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private static void respond(HttpExchange request) throws IOException {
        FORWARDED.incrementAndGet();
        CAPTURED.set(new Captured(request.getRequestMethod(), request.getRequestURI().toASCIIString(),
                request.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION),
                request.getRequestHeaders().getFirst("X-User-Id"), request.getRequestHeaders().getFirst("X-User-Role"),
                request.getRequestHeaders().getFirst("X-Correlation-Id"),
                new String(request.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        byte[] response = RESPONSE.get().getBytes(StandardCharsets.UTF_8);
        request.getResponseHeaders().set("Content-Type", "application/json");
        request.getResponseHeaders().set("X-Correlation-Id", CAPTURED.get().correlation());
        request.sendResponseHeaders(STATUS.get(), response.length);
        try (var output = request.getResponseBody()) {
            output.write(response);
        }
    }
}
