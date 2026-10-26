package com.mediflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.common.security.Roles;
import com.mediflow.gateway.auth.OrganizationAuthClient;
import com.mediflow.gateway.security.JwtTokenService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes",
        "eureka.client.enabled=false",
        "spring.cloud.gateway.discovery.locator.enabled=false"
})
@AutoConfigureWebTestClient
class ClinicalLabOrderGatewayWebTest {
    // Route/forwarding contract fixture. AccountSessionValidationWebTest exercises the real guard.
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.mediflow.gateway.filter.AccountSessionValidationFilter sessionValidation;

    @org.junit.jupiter.api.BeforeEach
    void isolateAccountStateDependency() {
        org.mockito.Mockito.when(sessionValidation.filter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> ((org.springframework.cloud.gateway.filter.GatewayFilterChain) call.getArgument(1))
                        .filter(call.getArgument(0)));
    }

    private static final AtomicInteger FORWARDED = new AtomicInteger();
    private static final AtomicReference<String> PATH = new AtomicReference<>();
    private static final AtomicReference<String> METHOD = new AtomicReference<>();
    private static final AtomicReference<String> BODY = new AtomicReference<>();
    private static final AtomicReference<String> ACCOUNT = new AtomicReference<>();
    private static final AtomicReference<String> STAFF = new AtomicReference<>();
    private static final AtomicReference<String> ROLE = new AtomicReference<>();
    private static HttpServer clinical;

    @Autowired WebTestClient http;
    @Autowired JwtTokenService tokens;
    @MockBean OrganizationAuthClient organization;

    @DynamicPropertySource
    static void discoverClinical(DynamicPropertyRegistry registry) throws IOException {
        clinical = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        clinical.createContext("/api/v1/records/", ClinicalLabOrderGatewayWebTest::respond);
        clinical.start();
        registry.add("spring.cloud.discovery.client.simple.instances.clinical-service[0].uri",
                () -> "http://127.0.0.1:" + clinical.getAddress().getPort());
    }

    @BeforeEach
    void reset() {
        FORWARDED.set(0);
    }

    @AfterAll
    static void stop() {
        if (clinical != null) clinical.stop(0);
    }

    @Test
    void exactPost_forwardsBodyAndSignedDoctorIdentityToClinical() {
        UUID accountId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        String token = tokens.issueAccessToken(accountId, Roles.DOCTOR, staffId, departmentId, null);
        String path = "/api/v1/records/" + UUID.randomUUID() + "/lab-orders";
        String body = "{\"labType\":\"CBC\",\"priceCode\":\"LAB_CBC\",\"requestedDate\":\"2026-10-16\"}";

        http.post().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-Correlation-Id", correlationId)
                .header("X-User-Role", Roles.ADMIN)
                .header("X-Staff-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                .exchange().expectStatus().isCreated()
                .expectHeader().valueEquals("X-Correlation-Id", correlationId)
                .expectBody().jsonPath("$.success").isEqualTo(true);

        assertThat(FORWARDED.get()).isEqualTo(1);
        assertThat(PATH.get()).isEqualTo(path);
        assertThat(METHOD.get()).isEqualTo("POST");
        assertThat(BODY.get()).isEqualTo(body);
        assertThat(ACCOUNT.get()).isEqualTo(accountId.toString());
        assertThat(STAFF.get()).isEqualTo(staffId.toString());
        assertThat(ROLE.get()).isEqualTo(Roles.DOCTOR);
    }

    @Test
    void wrongRoleMethodPathOrTokenType_neverReachesClinical() {
        String path = "/api/v1/records/" + UUID.randomUUID() + "/lab-orders";
        String doctor = tokens.issueAccessToken(UUID.randomUUID(), Roles.DOCTOR);
        String nurse = tokens.issueAccessToken(UUID.randomUUID(), Roles.NURSE);

        http.post().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + nurse)
                .exchange().expectStatus().isForbidden();
        http.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + doctor)
                .exchange().expectStatus().isForbidden();
        http.put().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + doctor)
                .exchange().expectStatus().isForbidden();
        http.post().uri(path + "/extra").header(HttpHeaders.AUTHORIZATION, "Bearer " + doctor)
                .exchange().expectStatus().isForbidden();
        http.post().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer "
                + tokens.issueRefreshToken(UUID.randomUUID(), Roles.DOCTOR))
                .exchange().expectStatus().isUnauthorized();
        http.post().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer "
                + tokens.issueServiceToken("clinical-service", Roles.SYSTEM, UUID.randomUUID().toString()))
                .exchange().expectStatus().isForbidden();
        assertThat(FORWARDED.get()).isZero();
    }

    private static void respond(HttpExchange request) throws IOException {
        FORWARDED.incrementAndGet();
        PATH.set(request.getRequestURI().getPath());
        METHOD.set(request.getRequestMethod());
        BODY.set(new String(request.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        ACCOUNT.set(request.getRequestHeaders().getFirst("X-User-Id"));
        STAFF.set(request.getRequestHeaders().getFirst("X-Staff-Id"));
        ROLE.set(request.getRequestHeaders().getFirst("X-User-Role"));
        String correlationId = request.getRequestHeaders().getFirst("X-Correlation-Id");
        byte[] response = ("{\"success\":true,\"data\":{\"sourceOrderId\":\""
                + UUID.randomUUID() + "\"},\"correlationId\":\"" + correlationId + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        request.getResponseHeaders().set("Content-Type", "application/json");
        request.getResponseHeaders().set("X-Correlation-Id", correlationId);
        request.sendResponseHeaders(201, response.length);
        try (var output = request.getResponseBody()) {
            output.write(response);
        }
    }
}
