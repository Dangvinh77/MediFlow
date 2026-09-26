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
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

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
    private final OrganizationAuthProperties properties;
    private final CircuitBreaker circuitBreaker;

    public OrganizationAuthClient(
            WebClient.Builder webClientBuilder,
            JwtTokenService jwt,
            OrganizationAuthProperties properties) {
        this.client = webClientBuilder.build();
        this.jwt = jwt;
        this.properties = properties;
        this.circuitBreaker = CircuitBreaker.of(
                "organization-auth",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(properties.failureRateThreshold())
                        .slidingWindowSize(properties.slidingWindowSize())
                        .minimumNumberOfCalls(properties.minimumNumberOfCalls())
                        .waitDurationInOpenState(Duration.ofSeconds(properties.waitDurationSeconds()))
                        .ignoreExceptions(InvalidCredentialsException.class)
                        .build());
    }

    /** Convenient constructor for focused unit tests using the production defaults. */
    public OrganizationAuthClient(WebClient.Builder webClientBuilder, JwtTokenService jwt) {
        this(webClientBuilder, jwt, new OrganizationAuthProperties(1500, 50, 10, 5, 10));
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
                .timeout(Duration.ofMillis(properties.timeoutMillis()))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorMap(exception -> exception instanceof InvalidCredentialsException
                        || exception instanceof UpstreamUnavailableException
                        ? exception
                        : exception instanceof TimeoutException
                        ? new UpstreamTimeoutException(
                                "Organization account verification timed out", exception)
                        : exception instanceof CallNotPermittedException
                        ? new UpstreamUnavailableException(
                                "Organization account verification circuit is open", exception)
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

    public static class UpstreamTimeoutException extends UpstreamUnavailableException {
        public UpstreamTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
