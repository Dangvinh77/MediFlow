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
import org.springframework.http.HttpMethod;
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
    void organizationRoomLookup_isServiceOnlyAndPreservesCorrelation() {
        UUID roomId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        String serviceToken = jwt.issueServiceToken("gateway-test", Roles.SYSTEM, correlationId);

        webTestClient.get()
                .uri("/api/v1/org/rooms/{id}/lookup", roomId)
                .header(HttpHeaders.AUTHORIZATION, bearer(serviceToken))
                .header("X-Correlation-Id", correlationId)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader().valueEquals("X-Correlation-Id", correlationId)
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("GATEWAY_UPSTREAM_UNAVAILABLE");

        String humanToken = jwt.issueAccessToken(UUID.randomUUID(), Roles.ADMIN);
        webTestClient.get()
                .uri("/api/v1/org/rooms/{id}/lookup", roomId)
                .header(HttpHeaders.AUTHORIZATION, bearer(humanToken))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("AUTH_FORBIDDEN");
    }

    @Test
    void inpatientRoute_getUsesCoarseClinicalRoleMatrix() {
        String doctorToken = jwt.issueAccessToken(UUID.randomUUID(), Roles.DOCTOR);
        String correlationId = UUID.randomUUID().toString();

        webTestClient.get()
                .uri("/api/v1/inpatient/admissions")
                .header(HttpHeaders.AUTHORIZATION, bearer(doctorToken))
                .header("X-Correlation-Id", correlationId)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader().valueEquals("X-Correlation-Id", correlationId)
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("GATEWAY_UPSTREAM_UNAVAILABLE");
    }

    @Test
    void inpatientRoute_postAndPutFollowDownstreamRoleMatrix() {
        String doctorToken = jwt.issueAccessToken(UUID.randomUUID(), Roles.DOCTOR);
        String nurseToken = jwt.issueAccessToken(UUID.randomUUID(), Roles.NURSE);

        webTestClient.post()
                .uri("/api/v1/inpatient/admissions")
                .header(HttpHeaders.AUTHORIZATION, bearer(doctorToken))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        webTestClient.post()
                .uri("/api/v1/inpatient/admissions")
                .header(HttpHeaders.AUTHORIZATION, bearer(nurseToken))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("AUTH_FORBIDDEN");

        webTestClient.put()
                .uri("/api/v1/inpatient/admissions/{id}/bed", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearer(nurseToken))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        webTestClient.put()
                .uri("/api/v1/inpatient/admissions/{id}/bed", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearer(doctorToken))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("AUTH_FORBIDDEN");
    }

    @Test
    void clinicalRoutes_followDownstreamRoleMatrix() {
        UUID appointmentId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();

        expectDownstreamUnavailable(HttpMethod.PUT,
                "/api/v1/appointments/" + appointmentId + "/check-in", Roles.NURSE);
        expectForbidden(HttpMethod.PUT,
                "/api/v1/appointments/" + appointmentId + "/check-in", Roles.DOCTOR);

        expectDownstreamUnavailable(HttpMethod.PUT,
                "/api/v1/appointments/" + appointmentId + "/start-exam", Roles.DOCTOR);
        expectForbidden(HttpMethod.PUT,
                "/api/v1/appointments/" + appointmentId + "/start-exam", Roles.NURSE);

        expectDownstreamUnavailable(HttpMethod.POST,
                "/api/v1/records/" + recordId + "/admission-referrals", Roles.DOCTOR);
        expectForbidden(HttpMethod.POST,
                "/api/v1/records/" + recordId + "/admission-referrals", Roles.NURSE);
    }

    @Test
    void labRoutes_followDownstreamRoleMatrix() {
        UUID testId = UUID.randomUUID();

        expectDownstreamUnavailable(HttpMethod.GET, "/api/v1/lab", Roles.ADMIN);
        expectDownstreamUnavailable(HttpMethod.GET, "/api/v1/lab", Roles.MANAGER);
        expectDownstreamUnavailable(HttpMethod.GET, "/api/v1/lab", Roles.DOCTOR);
        expectDownstreamUnavailable(HttpMethod.GET, "/api/v1/lab", Roles.NURSE);
        expectDownstreamUnavailable(HttpMethod.GET, "/api/v1/lab", Roles.LAB_TECH);

        expectDownstreamUnavailable(HttpMethod.GET, "/api/v1/lab/" + testId, Roles.ADMIN);
        expectDownstreamUnavailable(HttpMethod.GET, "/api/v1/lab/" + testId, Roles.DOCTOR);
        expectDownstreamUnavailable(HttpMethod.GET, "/api/v1/lab/" + testId, Roles.NURSE);
        expectDownstreamUnavailable(HttpMethod.GET, "/api/v1/lab/" + testId, Roles.LAB_TECH);
        expectForbidden(HttpMethod.GET, "/api/v1/lab/" + testId, Roles.MANAGER);

        expectDownstreamUnavailable(HttpMethod.PUT,
                "/api/v1/lab/" + testId + "/start", Roles.ADMIN);
        expectDownstreamUnavailable(HttpMethod.PUT,
                "/api/v1/lab/" + testId + "/start", Roles.LAB_TECH);
        expectForbidden(HttpMethod.PUT,
                "/api/v1/lab/" + testId + "/start", Roles.DOCTOR);
        expectForbidden(HttpMethod.PUT,
                "/api/v1/lab/" + testId + "/start", Roles.MANAGER);

        expectDownstreamUnavailable(HttpMethod.PUT,
                "/api/v1/lab/" + testId + "/cancel", Roles.ADMIN);
        expectDownstreamUnavailable(HttpMethod.PUT,
                "/api/v1/lab/" + testId + "/cancel", Roles.DOCTOR);
        expectDownstreamUnavailable(HttpMethod.PUT,
                "/api/v1/lab/" + testId + "/cancel", Roles.LAB_TECH);
        expectForbidden(HttpMethod.PUT,
                "/api/v1/lab/" + testId + "/cancel", Roles.NURSE);
        expectForbidden(HttpMethod.PUT,
                "/api/v1/lab/" + testId + "/cancel", Roles.MANAGER);

        expectDownstreamUnavailable(HttpMethod.PUT,
                "/api/v1/lab/" + testId + "/status", Roles.LAB_TECH);
        expectForbidden(HttpMethod.PUT,
                "/api/v1/lab/" + testId + "/status", Roles.DOCTOR);
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

    private void expectDownstreamUnavailable(HttpMethod method, String path, String role) {
        request(method, path, role)
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("GATEWAY_UPSTREAM_UNAVAILABLE");
    }

    private void expectForbidden(HttpMethod method, String path, String role) {
        request(method, path, role)
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error.code").isEqualTo("AUTH_FORBIDDEN");
    }

    private WebTestClient.ResponseSpec request(HttpMethod method, String path, String role) {
        String token = jwt.issueAccessToken(UUID.randomUUID(), role);
        return webTestClient.method(method)
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange();
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
