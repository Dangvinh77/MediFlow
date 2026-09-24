package com.mediflow.gateway.auth;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.Roles;
import com.mediflow.gateway.security.JwtTokenService;
import com.mediflow.gateway.security.JwtTokenService.InvalidCredentialsException;
import com.mediflow.gateway.security.JwtTokenService.UpstreamUnavailableException;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.core.ParameterizedTypeReference;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;

/** Reactive client for the Organization account-verification contract. */
@Service
public class OrganizationAuthClient {

    private static final String ORGANIZATION_URI =
            "http://organization-service/api/v1/org/accounts/verify";
    private static final Set<String> SUPPORTED_ROLES = Set.of(
            Roles.ADMIN,
            Roles.DOCTOR,
            Roles.NURSE,
            Roles.PHARMACIST,
            Roles.CASHIER,
            Roles.LAB_TECH,
            Roles.MANAGER,
            Roles.PATIENT);

    private final WebClient client;
    private final JwtTokenService jwt;

    public OrganizationAuthClient(WebClient.Builder webClientBuilder, JwtTokenService jwt) {
        this.client = webClientBuilder.build();
        this.jwt = jwt;
    }

    public Mono<VerifiedAccount> verify(
            String username,
            String password,
            String requestedCorrelationId) {

        String correlationId = normalizeCorrelationId(requestedCorrelationId);
        String serviceToken = jwt.issueServiceToken(
                "gateway",
                Roles.SYSTEM,
                correlationId);

        return client.post()
                .uri(ORGANIZATION_URI)
                .headers(headers -> {
                    headers.setBearerAuth(serviceToken);
                    headers.set("X-Correlation-Id", correlationId);
                })
                .bodyValue(new VerifyCredentialsRequest(username, password))
                .retrieve()
                .onStatus(status -> status.value() == 422, response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> body.contains("AUTH_INVALID_CREDENTIALS")
                                        ? Mono.error(new InvalidCredentialsException())
                                        : Mono.error(new UpstreamUnavailableException(
                                                "Organization returned an invalid credential error response"))))
                .onStatus(HttpStatusCode::isError, response ->
                        Mono.error(new UpstreamUnavailableException(
                                "Organization account verification failed with HTTP "
                                        + response.statusCode().value())))
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<VerifiedAccount>>() {})
                .switchIfEmpty(Mono.error(new UpstreamUnavailableException(
                        "Organization returned an empty account verification response")))
                .map(this::unwrap)
                .onErrorMap(exception -> exception instanceof InvalidCredentialsException
                        || exception instanceof UpstreamUnavailableException
                        ? exception
                        : new UpstreamUnavailableException(
                                "Organization account verification is unavailable", exception));
    }

    private VerifiedAccount unwrap(ApiResponse<VerifiedAccount> response) {
        if (!response.success() || response.data() == null) {
            throw new UpstreamUnavailableException(
                    "Organization returned an unsuccessful account verification response");
        }
        return validate(response.data());
    }

    private VerifiedAccount validate(VerifiedAccount account) {
        if (account.accountId() == null
                || !StringUtils.hasText(account.role())
                || !SUPPORTED_ROLES.contains(account.role())
                || Roles.SYSTEM.equals(account.role())) {
            throw new UpstreamUnavailableException(
                    "Organization returned an invalid account verification response");
        }
        if (Roles.PATIENT.equals(account.role())
                && (account.patientId() == null || account.staffId() != null)) {
            throw new UpstreamUnavailableException(
                    "Organization returned an incomplete patient identity");
        }
        if (!Roles.PATIENT.equals(account.role())
                && account.patientId() != null) {
            throw new UpstreamUnavailableException(
                    "Organization returned a patient identity for a staff account");
        }
        return account;
    }

    static String normalizeCorrelationId(String value) {
        if (StringUtils.hasText(value)) {
            try {
                return UUID.fromString(value).toString();
            } catch (IllegalArgumentException ignored) {
                // The downstream contract uses UUID correlation ids; create one for malformed input.
            }
        }
        return UUID.randomUUID().toString();
    }

    private record VerifyCredentialsRequest(String username, String password) {
    }

    public record VerifiedAccount(
            UUID accountId,
            UUID staffId,
            UUID departmentId,
            UUID patientId,
            String role) {
    }
}
