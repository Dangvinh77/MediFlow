package com.mediflow.gateway;

import com.mediflow.common.security.Roles;
import com.mediflow.gateway.auth.OrganizationAuthClient;
import com.mediflow.gateway.security.JwtTokenService;
import com.mediflow.gateway.security.JwtTokenService.UpstreamUnavailableException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes",
                "spring.cloud.discovery.enabled=false",
                "eureka.client.enabled=false",
                "spring.cloud.gateway.discovery.locator.enabled=false"
        })
@AutoConfigureWebTestClient
class GatewayWebTest {

    private static final String SECRET = "test-secret-must-have-at-least-32-bytes";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtTokenService jwt;

    @MockBean
    private OrganizationAuthClient organization;

    @Test
    void expiredTamperedAndWrongTypeTokens_return401Envelope() {
        String expired = expiredToken();
        String tampered = jwt.issueAccessToken(UUID.randomUUID(), Roles.ADMIN) + "tampered";
        String refresh = jwt.issueRefreshToken(UUID.randomUUID(), Roles.ADMIN);

        assertUnauthorized(expired);
        assertUnauthorized(tampered);
        assertUnauthorized(refresh);
    }

    @Test
    void forgedIdentityHeaders_doNotUpgradePatientRole() {
        String patientToken = jwt.issueAccessToken(
                UUID.randomUUID(), Roles.PATIENT, null, null, UUID.randomUUID());

        webTestClient.post()
                .uri("/api/v1/patients")
                .header(HttpHeaders.AUTHORIZATION, bearer(patientToken))
                .header("X-User-Role", Roles.ADMIN)
                .header("X-User-Id", UUID.randomUUID().toString())
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith("application/json")
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error.code").isEqualTo("AUTH_FORBIDDEN");
    }

    @Test
    void routeRoleMatrix_rejectsRoleNotListedForMethod() {
        String patientToken = jwt.issueAccessToken(
                UUID.randomUUID(), Roles.PATIENT, null, null, UUID.randomUUID());

        webTestClient.get()
                .uri("/api/v1/org/departments")
                .header(HttpHeaders.AUTHORIZATION, bearer(patientToken))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("AUTH_FORBIDDEN");
    }

    @Test
    void correlationId_isKeptWhenValidAndGeneratedWhenMissing() {
        String requested = UUID.randomUUID().toString();
        webTestClient.get()
                .uri("/actuator/health")
                .header("X-Correlation-Id", requested)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", requested);

        String generated = webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseHeaders()
                .getFirst("X-Correlation-Id");
        UUID.fromString(generated);
    }

    @Test
    void organizationOutage_returns503Envelope() {
        when(organization.verify(anyString(), anyString(), anyString()))
                .thenReturn(Mono.error(new UpstreamUnavailableException("down")));

        String correlationId = UUID.randomUUID().toString();
        webTestClient.post()
                .uri("/api/v1/auth/login")
                .header("X-Correlation-Id", correlationId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"admin\",\"password\":\"password\"}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader().valueEquals("X-Correlation-Id", correlationId)
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error.code").isEqualTo("AUTH_UPSTREAM_UNAVAILABLE")
                .jsonPath("$.correlationId").isEqualTo(correlationId);
    }

    @Test
    void routedOrganizationOutage_returns503Envelope() {
        String adminToken = jwt.issueAccessToken(UUID.randomUUID(), Roles.ADMIN);

        webTestClient.get()
                .uri("/api/v1/org/departments")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error.code").isEqualTo("GATEWAY_UPSTREAM_UNAVAILABLE");
    }

    @Test
    void organizationTimeout_returns504Envelope() {
        when(organization.verify(anyString(), anyString(), anyString()))
                .thenReturn(Mono.error(new OrganizationAuthClient.UpstreamTimeoutException(
                        "timeout", new java.util.concurrent.TimeoutException())));

        webTestClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"admin\",\"password\":\"password\"}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT)
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error.code").isEqualTo("AUTH_UPSTREAM_TIMEOUT");
    }

    private void assertUnauthorized(String token) {
        webTestClient.get()
                .uri("/api/v1/patients")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith("application/json")
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error.code").isEqualTo("AUTH_UNAUTHORIZED");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String expiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", Roles.ADMIN)
                .claim("type", "access")
                .issuedAt(Date.from(now.minusSeconds(120)))
                .expiration(Date.from(now.minusSeconds(60)))
                .signWith(key)
                .compact();
    }
}
