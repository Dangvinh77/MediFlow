package com.mediflow.gateway;

import com.mediflow.gateway.auth.OrganizationAuthClient;
import com.mediflow.gateway.auth.OrganizationAuthClient.CurrentIdentity;
import com.mediflow.gateway.security.JwtTokenService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes",
        "spring.cloud.discovery.enabled=false", "eureka.client.enabled=false",
        "spring.cloud.gateway.discovery.locator.enabled=false",
        "spring.cloud.gateway.routes[0].id=core-organization"
})
@AutoConfigureWebTestClient
class AccountSessionValidationWebTest {
    static final AtomicInteger FORWARDED = new AtomicInteger();
    static final HttpServer DOWNSTREAM;
    static {
        try {
            DOWNSTREAM = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            DOWNSTREAM.createContext("/", exchange -> {
                FORWARDED.incrementAndGet(); exchange.sendResponseHeaders(204, -1); exchange.close();
            });
            DOWNSTREAM.start();
        } catch (java.io.IOException error) { throw new ExceptionInInitializerError(error); }
    }
    @DynamicPropertySource
    static void route(DynamicPropertyRegistry properties) {
        properties.add("spring.cloud.gateway.routes[0].id", () -> "core-organization");
        properties.add("spring.cloud.gateway.routes[0].predicates[0]", () -> "Path=/api/v1/org/**,/api/v1/notifications/**");
        properties.add("spring.cloud.gateway.routes[0].uri", () -> "http://localhost:" + DOWNSTREAM.getAddress().getPort());
    }
    @AfterAll static void closeServer() { DOWNSTREAM.stop(0); }
    @Autowired WebTestClient web;
    @Autowired JwtTokenService jwt;
    @MockBean OrganizationAuthClient accounts;
    final UUID account = UUID.randomUUID(), staff = UUID.randomUUID(), department = UUID.randomUUID();

    @BeforeEach void resetCount() { FORWARDED.set(0); }

    private CurrentIdentity identity(boolean active, String role, long version) {
        return new CurrentIdentity(true, active, account, role, staff, department, null, version);
    }
    private String access(long version) {
        return jwt.issueAccessToken(account, "NURSE", staff, department, null, version);
    }
    private WebTestClient.ResponseSpec request(String token) {
        return web.get().uri("/api/v1/org/staff/" + staff)
                .header("Authorization", "Bearer " + token)
                .header("X-Service-Id", "gateway") // A spoofed service header must never bypass the guard.
                .exchange();
    }

    @Test void currentSessionPassesAndNextRequestObservesCommittedLock() {
        when(accounts.lookupCurrentIdentity(eq(account), anyString()))
                .thenReturn(Mono.just(identity(true, "NURSE", 2)), Mono.just(identity(false, "NURSE", 3)));
        String token = access(2);
        request(token).expectStatus().isNoContent();
        request(token).expectStatus().isUnauthorized().expectBody().jsonPath("$.error.code").isEqualTo("AUTH_SESSION_REVOKED");
        assertThat(FORWARDED.get()).isEqualTo(1);
        verify(accounts, times(2)).lookupCurrentIdentity(eq(account), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"VERSION", "ROLE", "STAFF", "DEPARTMENT", "PATIENT", "MISSING"})
    void rejectsStaleCredentialsOrIdentityBeforeForwarding(String scenario) {
        CurrentIdentity current = switch (scenario) {
            case "VERSION" -> identity(true, "NURSE", 3);
            case "ROLE" -> identity(true, "ADMIN", 2);
            case "STAFF" -> new CurrentIdentity(true, true, account, "NURSE", UUID.randomUUID(), department, null, 2);
            case "DEPARTMENT" -> new CurrentIdentity(true, true, account, "NURSE", staff, UUID.randomUUID(), null, 2);
            case "PATIENT" -> new CurrentIdentity(true, true, account, "PATIENT", null, null, UUID.randomUUID(), 2);
            default -> new CurrentIdentity(false, false, null, null, null, null, null, 0);
        };
        when(accounts.lookupCurrentIdentity(eq(account), anyString())).thenReturn(Mono.just(current));
        request(access(2)).expectStatus().isUnauthorized();
        assertThat(FORWARDED.get()).isZero();
    }

    @Test void passwordResetCannotBeUndoneByReactivatingTheAccount() {
        when(accounts.lookupCurrentIdentity(eq(account), anyString())).thenReturn(Mono.just(identity(true, "NURSE", 5)));
        request(access(2)).expectStatus().isUnauthorized();
        request(access(5)).expectStatus().isNoContent();
        assertThat(FORWARDED.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MANAGER", "DOCTOR", "NURSE", "PHARMACIST", "CASHIER", "LAB_TECH", "PATIENT"})
    void defaultPasswordAllowsOnlyOwnPasswordChangeEvenIfClaimIsMissing(String role) {
        UUID staffId = role.equals("PATIENT") ? null : staff;
        UUID patientId = role.equals("PATIENT") ? UUID.randomUUID() : null;
        UUID departmentId = role.equals("PATIENT") ? null : department;
        when(accounts.lookupCurrentIdentity(eq(account), anyString())).thenReturn(Mono.just(
                new CurrentIdentity(true, true, account, role, staffId, departmentId, patientId, 2, true)));
        // Deliberately omit the restriction from the token: current server authority still wins.
        String token = jwt.issueAccessToken(account, role, staffId, departmentId, patientId, 2);
        String businessPath = role.equals("PATIENT") ? "/api/v1/notifications" : "/api/v1/org/staff/" + staff;
        web.get().uri(businessPath).header("Authorization", "Bearer " + token)
                .exchange().expectStatus().isForbidden().expectBody()
                .jsonPath("$.error.code").isEqualTo("AUTH_PASSWORD_CHANGE_REQUIRED");
        web.put().uri("/api/v1/org/accounts/me/password").header("Authorization", "Bearer " + token)
                .exchange().expectStatus().isNoContent();
        assertThat(FORWARDED.get()).isEqualTo(1);
    }

    @Test void restrictedAdminCannotManageOtherAccountsOrUseDifferentPasswordMethods() {
        when(accounts.lookupCurrentIdentity(eq(account), anyString())).thenReturn(Mono.just(
                new CurrentIdentity(true, true, account, "ADMIN", staff, department, null, 2, true)));
        String token = jwt.issueAccessToken(account, "ADMIN", staff, department, null, 2, true);
        web.put().uri("/api/v1/org/accounts/" + UUID.randomUUID() + "/password")
                .header("Authorization", "Bearer " + token).exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.error.code").isEqualTo("AUTH_PASSWORD_CHANGE_REQUIRED");
        web.get().uri("/api/v1/org/accounts/me/password").header("Authorization", "Bearer " + token)
                .exchange().expectStatus().isForbidden();
        assertThat(FORWARDED.get()).isZero();
    }

    @Test void accountAuthorityUnavailableFailsClosedWithoutForwarding() {
        when(accounts.lookupCurrentIdentity(eq(account), anyString()))
                .thenReturn(Mono.error(new JwtTokenService.UpstreamUnavailableException("offline")));
        request(access(2)).expectStatus().isEqualTo(503);
        assertThat(FORWARDED.get()).isZero();
    }

    @Test void serviceLookupAndPublicHealthDoNotQueryHumanAccountAuthority() {
        web.get().uri("/api/v1/org/staff/" + staff + "/lookup")
                .header("Authorization", "Bearer " + jwt.issueServiceToken("clinical-service", "SYSTEM", UUID.randomUUID().toString()))
                .exchange().expectStatus().isNoContent();
        web.get().uri("/actuator/health").exchange().expectStatus().isOk();
        verifyNoInteractions(accounts);
    }
}
