package com.mediflow.gateway;

import com.mediflow.common.security.Roles;
import com.mediflow.gateway.auth.OrganizationAuthClient;
import com.mediflow.gateway.security.JwtTokenService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes",
                "spring.cloud.discovery.enabled=false",
                "eureka.client.enabled=false",
                "spring.cloud.gateway.discovery.locator.enabled=false",
                "spring.cloud.gateway.routes[0].id=organization-service",
                "spring.cloud.gateway.routes[0].predicates[0]=Path=/api/v1/org/**"
        })
@AutoConfigureWebTestClient
class GatewayDownstreamResponseWebTest {

    private static final String DOWNSTREAM_BODY = "{\"success\":false,\"data\":null,"
            + "\"error\":{\"code\":\"ORG_VALIDATION_ERROR\","
            + "\"message\":\"invalid department\",\"details\":[]},"
            + "\"correlationId\":\"downstream\"}";
    private static final AtomicReference<String> downstreamUserId = new AtomicReference<>();
    private static final AtomicReference<String> downstreamRole = new AtomicReference<>();
    private static final AtomicReference<String> downstreamAccountId = new AtomicReference<>();
    private static final AtomicReference<String> downstreamAliasRole = new AtomicReference<>();
    private static HttpServer downstream;

    static {
        try {
            downstream = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            downstream.createContext("/", GatewayDownstreamResponseWebTest::respond);
            downstream.start();
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtTokenService jwt;

    @MockBean
    private OrganizationAuthClient organization;

    @AfterAll
    static void stopDownstream() {
        if (downstream != null) {
            downstream.stop(0);
        }
    }

    @DynamicPropertySource
    static void routeToStub(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.gateway.routes[0].id", () -> "organization-service");
        registry.add("spring.cloud.gateway.routes[0].uri",
                () -> "http://localhost:" + downstream.getAddress().getPort());
        registry.add("spring.cloud.gateway.routes[0].predicates[0]",
                () -> "Path=/api/v1/org/**");
    }

    @Test
    void downstream422_isPassedThroughWithoutGateway500() {
        String correlationId = UUID.randomUUID().toString();

        webTestClient.get()
                .uri("/api/v1/org/departments")
                .header("Authorization", "Bearer " + jwt.issueAccessToken(UUID.randomUUID(), Roles.ADMIN))
                .header("X-Correlation-Id", correlationId)
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectHeader().valueEquals("X-Correlation-Id", correlationId)
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("ORG_VALIDATION_ERROR");
    }

    @Test
    void forgedIdentityHeaders_areRemovedAndReplacedFromVerifiedClaims() {
        UUID accountId = UUID.randomUUID();

        webTestClient.get()
                .uri("/api/v1/org/departments")
                .header("Authorization", "Bearer " + jwt.issueAccessToken(accountId, Roles.ADMIN))
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Role", Roles.PATIENT)
                .header("X-Account-Id", UUID.randomUUID().toString())
                .header("X-Role", Roles.PATIENT)
                .exchange()
                .expectStatus().isEqualTo(422);

        assertThat(downstreamUserId.get()).isEqualTo(accountId.toString());
        assertThat(downstreamRole.get()).isEqualTo(Roles.ADMIN);
        assertThat(downstreamAccountId.get()).isNull();
        assertThat(downstreamAliasRole.get()).isNull();
    }

    private static void respond(HttpExchange exchange) throws IOException {
        downstreamUserId.set(exchange.getRequestHeaders().getFirst("X-User-Id"));
        downstreamRole.set(exchange.getRequestHeaders().getFirst("X-User-Role"));
        downstreamAccountId.set(exchange.getRequestHeaders().getFirst("X-Account-Id"));
        downstreamAliasRole.set(exchange.getRequestHeaders().getFirst("X-Role"));
        byte[] body = DOWNSTREAM_BODY.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(422, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
