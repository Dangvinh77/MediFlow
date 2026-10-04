package com.mediflow.gateway.auth;

import com.mediflow.gateway.auth.AuthDtos.LoginRequest;
import com.mediflow.gateway.auth.AuthDtos.LoginResponse;
import com.mediflow.gateway.security.JwtProperties;
import com.mediflow.gateway.security.JwtTokenService;
import com.mediflow.gateway.security.JwtTokenService.InvalidCredentialsException;
import com.mediflow.gateway.security.JwtTokenService.UpstreamUnavailableException;
import com.mediflow.common.security.JwtClaims;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private static final String SECRET = "test-secret-must-have-at-least-32-bytes";

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
}
