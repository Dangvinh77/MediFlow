package com.mediflow.gateway.auth;

import com.mediflow.gateway.auth.AuthDtos.LoginRequest;
import com.mediflow.gateway.auth.AuthDtos.LoginResponse;
import com.mediflow.gateway.auth.AuthDtos.RefreshRequest;
import com.mediflow.gateway.security.JwtProperties;
import com.mediflow.gateway.security.JwtTokenService;
import com.mediflow.gateway.security.JwtTokenService.InvalidCredentialsException;
import com.mediflow.gateway.security.JwtTokenService.UpstreamUnavailableException;
import com.mediflow.common.security.JwtClaims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import io.jsonwebtoken.security.Keys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private static final String SECRET = "test-secret-must-have-at-least-32-bytes";

    @Test
    void repeatedLoginAndRefreshPreserveRequiredPasswordChange() {
        JwtTokenService jwt = new JwtTokenService(new JwtProperties(SECRET, 30, 1440, 1));
        OrganizationAuthClient organization = mock(OrganizationAuthClient.class);
        UUID account = UUID.randomUUID(), patient = UUID.randomUUID();
        String correlation = UUID.randomUUID().toString();
        when(organization.verify("012345678901", "1", correlation)).thenReturn(Mono.just(
                new OrganizationAuthClient.VerifiedAccount(account, null, null, patient, "PATIENT", 0, true)));
        when(organization.lookupCurrentIdentity(account, correlation)).thenReturn(Mono.just(
                new OrganizationAuthClient.CurrentIdentity(true, true, account, "PATIENT", null, null, patient, 0, true)));
        var controller = new AuthController(jwt, organization);
        for (int attempt = 0; attempt < 2; attempt++) {
            var login = (LoginResponse) controller.login(new LoginRequest("012345678901", "1"), correlation).block().getBody();
            assertThat(jwt.parse(login.accessToken()).get("mustChangePassword", Boolean.class)).isTrue();
            var refresh = (AuthDtos.RefreshResponse) controller.refresh(new RefreshRequest(login.refreshToken()), correlation).block().getBody();
            assertThat(jwt.parse(refresh.accessToken()).get("mustChangePassword", Boolean.class)).isTrue();
        }
    }

    @Test
    void refreshBeforePasswordChangeIsRejectedButNewLoginVersionWorks() {
        JwtTokenService jwt = new JwtTokenService(new JwtProperties(SECRET, 30, 1440, 1));
        OrganizationAuthClient organization = mock(OrganizationAuthClient.class);
        UUID accountId = UUID.randomUUID(), staffId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        when(organization.lookupCurrentIdentity(accountId, correlationId))
                .thenReturn(Mono.just(new OrganizationAuthClient.CurrentIdentity(
                        true, true, accountId, "DOCTOR", staffId, null, null, 1)));
        var controller = new AuthController(jwt, organization);
        var oldToken = jwt.issueRefreshToken(accountId, "DOCTOR", staffId, null, null, 0);
        assertThat(controller.refresh(new RefreshRequest(oldToken), correlationId).block().getStatusCode().value())
                .isEqualTo(401);
        var newToken = jwt.issueRefreshToken(accountId, "DOCTOR", staffId, null, null, 1);
        var refreshed = controller.refresh(new RefreshRequest(newToken), correlationId).block();
        assertThat(refreshed.getStatusCode().value()).isEqualTo(200);
        var refreshedBody = (AuthDtos.RefreshResponse) refreshed.getBody();
        assertThat(jwt.parse(refreshedBody.accessToken()).get(JwtTokenService.CREDENTIAL_VERSION_CLAIM, Number.class).longValue()).isEqualTo(1);
        when(organization.verify("doctor", "new-password", correlationId)).thenReturn(Mono.just(
                new OrganizationAuthClient.VerifiedAccount(accountId, staffId, null, null, "DOCTOR", 1)));
        var login = (LoginResponse) controller.login(new LoginRequest("doctor", "new-password"), correlationId).block().getBody();
        assertThat(jwt.parse(login.accessToken()).get(JwtTokenService.CREDENTIAL_VERSION_CLAIM, Number.class).longValue()).isEqualTo(1);
        assertThat(jwt.parse(login.refreshToken()).get(JwtTokenService.CREDENTIAL_VERSION_CLAIM, Number.class).longValue())
                .isEqualTo(1);
    }

    @Test
    void login_mintsTokensFromOrganizationIdentity() {
        JwtTokenService jwt = new JwtTokenService(new JwtProperties(SECRET, 30, 1440, 1));
        OrganizationAuthClient organization = mock(OrganizationAuthClient.class);
        UUID accountId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(organization.verify("admin", "password", "00000000-0000-0000-0000-000000000010"))
                .thenReturn(Mono.just(new OrganizationAuthClient.VerifiedAccount(
                        accountId,
                        UUID.randomUUID(),
                        departmentId,
                        null,
                        "ADMIN")));
        AuthController controller = new AuthController(jwt, organization);

        var response = controller.login(
                        new LoginRequest("admin", "password"),
                        "00000000-0000-0000-0000-000000000010")
                .block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        LoginResponse body = (LoginResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(jwt.parse(body.accessToken()).getSubject()).isEqualTo(accountId.toString());
        assertThat(jwt.parse(body.accessToken()).get("type", String.class)).isEqualTo("access");
        assertThat(jwt.parse(body.refreshToken()).get("type", String.class)).isEqualTo("refresh");
        assertThat(jwt.parse(body.accessToken()).get(
                JwtTokenService.DEPARTMENT_ID_CLAIM,
                String.class))
                .isEqualTo(departmentId.toString());
    }

    @Test
    void patientLoginKeepsAccountSubjectSeparateFromPatientIdentity() {
        JwtTokenService jwt = new JwtTokenService(new JwtProperties(SECRET, 30, 1440, 1));
        OrganizationAuthClient organization = mock(OrganizationAuthClient.class);
        UUID accountId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        String correlation = UUID.randomUUID().toString();
        when(organization.verify("patient01", "password", correlation))
                .thenReturn(Mono.just(new OrganizationAuthClient.VerifiedAccount(
                        accountId, null, null, patientId, "PATIENT")));
        AuthController controller = new AuthController(jwt, organization);

        var response = controller.login(
                        new LoginRequest("patient01", "password"), correlation)
                .block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        LoginResponse body = (LoginResponse) response.getBody();
        assertThat(body).isNotNull();

        var access = jwt.parse(body.accessToken());
        assertThat(access.getSubject()).isEqualTo(accountId.toString());
        assertThat(access.get("type", String.class)).isEqualTo("access");
        assertThat(access.get("role", String.class)).isEqualTo("PATIENT");
        assertThat(access.get(JwtClaims.PATIENT_ID, String.class))
                .isEqualTo(patientId.toString());
        assertThat(access.get(JwtClaims.STAFF_ID, String.class)).isNull();
    }

    @Test
    void login_returns401ForConfirmedInvalidCredentials() {
        JwtTokenService jwt = new JwtTokenService(new JwtProperties(SECRET, 30, 1440, 1));
        OrganizationAuthClient organization = mock(OrganizationAuthClient.class);
        when(organization.verify("admin", "bad", "00000000-0000-0000-0000-000000000010"))
                .thenReturn(Mono.error(new InvalidCredentialsException()));
        AuthController controller = new AuthController(jwt, organization);

        var response = controller.login(
                        new LoginRequest("admin", "bad"),
                        "00000000-0000-0000-0000-000000000010")
                .block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        var body = (com.mediflow.common.api.ApiResponse<?>) response.getBody();
        assertThat(body.success()).isFalse();
        assertThat(body.error().code()).isEqualTo("AUTH_INVALID_CREDENTIALS");
        assertThat(body.correlationId())
                .isEqualTo("00000000-0000-0000-0000-000000000010");
    }

    @Test
    void login_returns503ForOrganizationOutage() {
        JwtTokenService jwt = new JwtTokenService(new JwtProperties(SECRET, 30, 1440, 1));
        OrganizationAuthClient organization = mock(OrganizationAuthClient.class);
        when(organization.verify("admin", "password", "00000000-0000-0000-0000-000000000010"))
                .thenReturn(Mono.error(new UpstreamUnavailableException("down")));
        AuthController controller = new AuthController(jwt, organization);

        var response = controller.login(
                        new LoginRequest("admin", "password"),
                        "00000000-0000-0000-0000-000000000010")
                .block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        var body = (com.mediflow.common.api.ApiResponse<?>) response.getBody();
        assertThat(body.success()).isFalse();
        assertThat(body.error().code()).isEqualTo("AUTH_UPSTREAM_UNAVAILABLE");
        assertThat(body.correlationId())
                .isEqualTo("00000000-0000-0000-0000-000000000010");
    }

    @Test
    void refresh_withoutExpiration_isRejected() {
        JwtTokenService jwt = new JwtTokenService(new JwtProperties(SECRET, 30, 1440, 1));
        OrganizationAuthClient organization = mock(OrganizationAuthClient.class);
        UUID accountId = UUID.randomUUID();
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject(accountId.toString())
                .claim(JwtClaims.TYPE, JwtClaims.REFRESH_TOKEN_TYPE)
                .claim(JwtClaims.ROLE, "ADMIN")
                .issuedAt(Date.from(Instant.now()))
                .signWith(key)
                .compact();

        var response = new AuthController(jwt, organization).refresh(
                new RefreshRequest(token), "00000000-0000-0000-0000-000000000010").block();

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void refresh_lockedAccount_isRejectedAfterCurrentIdentityLookup() {
        JwtTokenService jwt = new JwtTokenService(new JwtProperties(SECRET, 30, 1440, 1));
        UUID accountId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        String correlationId = "00000000-0000-0000-0000-000000000010";
        String refreshToken = jwt.issueRefreshToken(accountId, "ADMIN");
        AtomicReference<org.springframework.web.reactive.function.client.ClientRequest> requestRef =
                new AtomicReference<>();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            requestRef.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"success\":true,\"data\":{"
                            + "\"exists\":true,\"active\":false,"
                            + "\"accountId\":\"" + accountId + "\","
                            + "\"role\":\"ADMIN\",\"staffId\":\"" + staffId + "\","
                            + "\"departmentId\":null,\"patientId\":null},"
                            + "\"error\":null,\"timestamp\":\"2026-10-21T00:00:00Z\","
                            + "\"correlationId\":\"" + correlationId + "\"}")
                    .build());
        });
        AuthController controller = new AuthController(jwt, new OrganizationAuthClient(builder, jwt));

        var response = controller.refresh(new RefreshRequest(refreshToken), correlationId).block();

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(requestRef.get()).isNotNull();
        assertThat(requestRef.get().method()).isEqualTo(org.springframework.http.HttpMethod.GET);
        assertThat(requestRef.get().url().getPath())
                .isEqualTo("/api/v1/org/accounts/" + accountId + "/identity");
    }

    @Test
    void refresh_usesCoreRoleAndIdentityInsteadOfStaleRefreshClaims() {
        JwtTokenService jwt = new JwtTokenService(new JwtProperties(SECRET, 30, 1440, 1));
        UUID accountId = UUID.randomUUID();
        UUID currentStaffId = UUID.randomUUID();
        UUID currentDepartmentId = UUID.randomUUID();
        String correlationId = "00000000-0000-0000-0000-000000000010";
        String refreshToken = jwt.issueRefreshToken(
                accountId, "ADMIN", UUID.randomUUID(), UUID.randomUUID(), null);
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"success\":true,\"data\":{"
                                + "\"exists\":true,\"active\":true,"
                                + "\"accountId\":\"" + accountId + "\","
                                + "\"role\":\"MANAGER\","
                                + "\"staffId\":\"" + currentStaffId + "\","
                                + "\"departmentId\":\"" + currentDepartmentId + "\","
                                + "\"patientId\":null},\"error\":null,"
                                + "\"timestamp\":\"2026-10-21T00:00:00Z\","
                                + "\"correlationId\":\"" + correlationId + "\"}")
                        .build()));
        AuthController controller = new AuthController(jwt, new OrganizationAuthClient(builder, jwt));

        var response = controller.refresh(new RefreshRequest(refreshToken), correlationId).block();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var body = (com.mediflow.gateway.auth.AuthDtos.RefreshResponse) response.getBody();
        assertThat(body).isNotNull();
        var access = jwt.parse(body.accessToken());
        assertThat(access.get(JwtClaims.ROLE, String.class)).isEqualTo("MANAGER");
        assertThat(access.get(JwtClaims.STAFF_ID, String.class)).isEqualTo(currentStaffId.toString());
        assertThat(access.get(JwtClaims.DEPARTMENT_ID, String.class))
                .isEqualTo(currentDepartmentId.toString());
        assertThat(access.getSubject()).isEqualTo(accountId.toString());
    }

    @Test
    void refresh_coreUnavailable_failsClosedAsUnavailable() {
        JwtTokenService jwt = new JwtTokenService(new JwtProperties(SECRET, 30, 1440, 1));
        UUID accountId = UUID.randomUUID();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> Mono.just(
                ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build()));
        AuthController controller = new AuthController(jwt, new OrganizationAuthClient(builder, jwt));

        var response = controller.refresh(
                new RefreshRequest(jwt.issueRefreshToken(accountId, "ADMIN")),
                "00000000-0000-0000-0000-000000000010").block();

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        var body = (com.mediflow.common.api.ApiResponse<?>) response.getBody();
        assertThat(body.error().code()).isEqualTo("AUTH_UPSTREAM_UNAVAILABLE");
    }
}
