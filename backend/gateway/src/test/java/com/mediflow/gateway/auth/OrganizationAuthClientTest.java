package com.mediflow.gateway.auth;

import com.mediflow.gateway.security.JwtProperties;
import com.mediflow.gateway.security.JwtTokenService;
import com.mediflow.gateway.security.JwtTokenService.InvalidCredentialsException;
import com.mediflow.gateway.security.JwtTokenService.UpstreamUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationAuthClientTest {

    private static final String SECRET = "test-secret-must-have-at-least-32-bytes";
    private static final JwtTokenService JWT = new JwtTokenService(
            new JwtProperties(SECRET, 30, 1440, 1));

    @Test
    void verify_sendsSystemServiceJwtAndCorrelationId() {
        AtomicReference<org.springframework.web.reactive.function.client.ClientRequest> requestRef =
                new AtomicReference<>();
        WebClient.Builder builder = WebClient.builder()
                .exchangeFunction(request -> {
                    requestRef.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body("{\"success\":true,\"data\":{"
                                    + "\"accountId\":\"00000000-0000-0000-0000-000000000001\","
                                    + "\"staffId\":null,\"departmentId\":null,\"role\":\"ADMIN\"},"
                                    + "\"error\":null,\"timestamp\":\"2026-09-18T00:00:00Z\","
                                    + "\"correlationId\":\"00000000-0000-0000-0000-000000000010\"}")
                            .build());
                });
        OrganizationAuthClient client = new OrganizationAuthClient(builder, JWT);

        var account = client.verify(
                        "admin",
                        "password",
                        "00000000-0000-0000-0000-000000000010")
                .block();
        assertThat(account).isNotNull();
        assertThat(account.role()).isEqualTo("ADMIN");

        var request = requestRef.get();
        assertThat(request.url().toString())
                .isEqualTo("http://organization-service/api/v1/org/accounts/verify");
        assertThat(request.headers().getFirst("X-Correlation-Id"))
                .isEqualTo("00000000-0000-0000-0000-000000000010");
        String serviceToken = request.headers().getFirst(HttpHeaders.AUTHORIZATION)
                .substring("Bearer ".length());
        assertThat(JWT.parse(serviceToken).getSubject()).isEqualTo("gateway");
        assertThat(JWT.parse(serviceToken).get("role", String.class)).isEqualTo("SYSTEM");
        assertThat(JWT.parse(serviceToken).get("type", String.class)).isEqualTo("service");
    }

    @Test
    void verify_mapsConfirmedInvalidCredentialsToAuthenticationFailure() {
        WebClient.Builder builder = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.UNPROCESSABLE_ENTITY)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"error\":\"AUTH_INVALID_CREDENTIALS\"}")
                        .build()));
        OrganizationAuthClient client = new OrganizationAuthClient(builder, JWT);

        assertThatThrownBy(() -> client.verify("admin", "bad", null).block())
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void verify_mapsUnavailableOrganizationToUpstreamFailure() {
        WebClient.Builder builder = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                        .build()));
        OrganizationAuthClient client = new OrganizationAuthClient(builder, JWT);

        assertThatThrownBy(() -> client.verify("admin", "password", null).block())
                .isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    void malformedCorrelationId_isReplacedWithUuid() {
        String normalized = OrganizationAuthClient.normalizeCorrelationId("not-a-uuid");

        assertThatCode(() -> UUID.fromString(normalized)).doesNotThrowAnyException();
    }

    @Test
    void verify_timesOutAsDedicatedUpstreamTimeout() {
        WebClient.Builder builder = WebClient.builder()
                .exchangeFunction(request -> Mono.never());
        OrganizationAuthClient client = new OrganizationAuthClient(
                builder,
                JWT,
                new OrganizationAuthProperties(10, 50, 10, 5, 10));

        assertThatThrownBy(() -> client.verify("admin", "password", null).block())
                .isInstanceOf(OrganizationAuthClient.UpstreamTimeoutException.class);
    }

    @Test
    void repeatedOrganizationFailures_openCircuitAndReturn503Classification() {
        WebClient.Builder builder = WebClient.builder()
                .exchangeFunction(request -> Mono.error(new ConnectException("down")));
        OrganizationAuthClient client = new OrganizationAuthClient(
                builder,
                JWT,
                new OrganizationAuthProperties(100, 50, 2, 1, 10));

        assertThatThrownBy(() -> client.verify("admin", "password", null).block())
                .isInstanceOf(UpstreamUnavailableException.class);
        assertThatThrownBy(() -> client.verify("admin", "password", null).block())
                .isInstanceOf(UpstreamUnavailableException.class);
    }
}
