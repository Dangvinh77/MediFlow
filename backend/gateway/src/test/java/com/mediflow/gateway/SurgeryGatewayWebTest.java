package com.mediflow.gateway;

import com.mediflow.common.security.Roles;
import com.mediflow.gateway.auth.OrganizationAuthClient;
import com.mediflow.gateway.security.JwtTokenService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes",
        "mediflow.routes.surgery.enabled=true", "eureka.client.enabled=false",
        "spring.cloud.gateway.discovery.locator.enabled=false"
})
@AutoConfigureWebTestClient
class SurgeryGatewayWebTest {

    private static final AtomicInteger REQUESTS = new AtomicInteger();
    private static final AtomicReference<String> PATH = new AtomicReference<>();
    private static final AtomicReference<String> BODY = new AtomicReference<>();
    private static final AtomicReference<String> TOKEN = new AtomicReference<>();
    private static final AtomicReference<String> ROLE = new AtomicReference<>();
    private static final AtomicReference<String> ACCOUNT = new AtomicReference<>();
    private static final AtomicReference<String> ALIAS = new AtomicReference<>();
    private static HttpServer downstream;

    @Autowired WebTestClient http;
    @Autowired JwtTokenService tokens;
    @Autowired RouteLocator routes;
    @MockBean OrganizationAuthClient organization;

    @DynamicPropertySource
    static void discoverSurgery(DynamicPropertyRegistry registry) throws IOException {
        downstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        downstream.createContext("/api/v1/surgery/", SurgeryGatewayWebTest::respond);
        downstream.start();
        registry.add("spring.cloud.discovery.client.simple.instances.surgery-service[0].uri",
                () -> "http://127.0.0.1:" + downstream.getAddress().getPort());
    }

    @BeforeEach
    void reset() { REQUESTS.set(0); }

    @AfterAll
    static void stop() { if (downstream != null) downstream.stop(0); }

    @Test
    void enabledRoute_usesDiscoveryAndPreservesPathBodyJwtCorrelationAndBusinessError() {
        assertThat(routes.getRoutes().filter(route -> "surgery-service".equals(route.getId()))
                .next().block().getUri()).isEqualTo(URI.create("lb://surgery-service"));
        UUID account = UUID.randomUUID();
        String jwt = tokens.issueAccessToken(account, Roles.DOCTOR);
        String correlation = UUID.randomUUID().toString();
        String body = "{\"expectedCaseRevision\":3}";
        String path = "/api/v1/surgery/cases/" + UUID.randomUUID() + "/preop";

        http.post().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .header("X-Correlation-Id", correlation).header("Idempotency-Key", "preop-1")
                .header("X-User-Role", Roles.ADMIN).header("X-User-Id", UUID.randomUUID().toString())
                .header("X-Role", Roles.ADMIN).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(body).exchange().expectStatus().isEqualTo(422)
                .expectHeader().valueEquals("X-Correlation-Id", correlation)
                .expectBody().jsonPath("$.error.code").isEqualTo("SURGERY_INVALID_TRANSITION")
                .jsonPath("$.correlationId").isEqualTo(correlation);

        assertThat(PATH.get()).isEqualTo(path);
        assertThat(BODY.get()).isEqualTo(body);
        assertThat(TOKEN.get()).isEqualTo("Bearer " + jwt);
        assertThat(ROLE.get()).isEqualTo(Roles.DOCTOR);
        assertThat(ACCOUNT.get()).isEqualTo(account.toString());
        assertThat(ALIAS.get()).isNull();
        assertThat(REQUESTS.get()).isEqualTo(1);
    }

    @Test
    void preopAndCancel_allowOnlyAdminAndDoctor_andUnknownCommandsRemainDenied() {
        for (String role : new String[] {Roles.ADMIN, Roles.DOCTOR}) {
            String token = tokens.issueAccessToken(UUID.randomUUID(), role);
            for (String command : new String[] {"preop", "cancel"}) {
                http.post().uri("/api/v1/surgery/cases/" + UUID.randomUUID() + "/" + command)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).bodyValue("{}")
                        .exchange().expectStatus().isEqualTo(422);
            }
            for (String command : new String[] {"start", "complete", "schedule/finalize",
                    "extra/preop", "cancel/extra"}) {
                http.post().uri("/api/v1/surgery/cases/" + UUID.randomUUID() + "/" + command)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).bodyValue("{}")
                        .exchange().expectStatus().isForbidden();
            }
        }
        int allowedCalls = REQUESTS.get();
        for (String role : new String[] {Roles.MANAGER, Roles.NURSE, Roles.PHARMACIST, Roles.CASHIER,
                Roles.LAB_TECH, Roles.PATIENT}) {
            String token = tokens.issueAccessToken(UUID.randomUUID(), role,
                    null, null, Roles.PATIENT.equals(role) ? UUID.randomUUID() : null);
            http.post().uri("/api/v1/surgery/cases/" + UUID.randomUUID() + "/preop")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).bodyValue("{}")
                    .exchange().expectStatus().isForbidden();
        }
        assertThat(REQUESTS.get()).isEqualTo(allowedCalls).isEqualTo(4);
    }

    @Test
    void unauthenticatedOrRefreshRequest_neverReachesSurgery() {
        String path = "/api/v1/surgery/cases/" + UUID.randomUUID() + "/cancel";
        http.post().uri(path).bodyValue("{}").exchange().expectStatus().isUnauthorized();
        http.post().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer "
                + tokens.issueRefreshToken(UUID.randomUUID(), Roles.ADMIN))
                .bodyValue("{}").exchange().expectStatus().isUnauthorized();
        assertThat(REQUESTS.get()).isZero();
    }

    private static void respond(HttpExchange request) throws IOException {
        REQUESTS.incrementAndGet();
        PATH.set(request.getRequestURI().getPath());
        BODY.set(new String(request.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        TOKEN.set(request.getRequestHeaders().getFirst("Authorization"));
        ROLE.set(request.getRequestHeaders().getFirst("X-User-Role"));
        ACCOUNT.set(request.getRequestHeaders().getFirst("X-User-Id"));
        ALIAS.set(request.getRequestHeaders().getFirst("X-Role"));
        String correlation = request.getRequestHeaders().getFirst("X-Correlation-Id");
        byte[] response = ("{\"success\":false,\"error\":{\"code\":\"SURGERY_INVALID_TRANSITION\"},"
                + "\"correlationId\":\"" + correlation + "\"}").getBytes(StandardCharsets.UTF_8);
        request.getResponseHeaders().set("Content-Type", "application/json");
        request.getResponseHeaders().set("X-Correlation-Id", correlation);
        request.sendResponseHeaders(422, response.length);
        try (var stream = request.getResponseBody()) { stream.write(response); }
    }
}
