package com.mediflow.gateway.auth;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.gateway.auth.AuthDtos.LoginRequest;
import com.mediflow.gateway.auth.AuthDtos.LoginResponse;
import com.mediflow.gateway.auth.AuthDtos.RefreshRequest;
import com.mediflow.gateway.auth.AuthDtos.RefreshResponse;
import com.mediflow.gateway.security.JwtTokenService;
import com.mediflow.gateway.security.JwtTokenService.InvalidCredentialsException;
import com.mediflow.gateway.security.JwtTokenService.UpstreamUnavailableException;
import com.mediflow.gateway.auth.OrganizationAuthClient.UpstreamTimeoutException;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Authentication facade for the public Gateway login and refresh endpoints.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JwtTokenService jwt;
    private final OrganizationAuthClient organizationAuthClient;

    public AuthController(
            JwtTokenService jwt,
            OrganizationAuthClient organizationAuthClient) {
        this.jwt = jwt;
        this.organizationAuthClient = organizationAuthClient;
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<Object>> login(
            @Valid @RequestBody LoginRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(
                    value = JwtClaims.HEADER_CORRELATION_ID,
                    required = false) String requestedCorrelationId) {

        String correlationId = OrganizationAuthClient.normalizeCorrelationId(
                requestedCorrelationId);
        return organizationAuthClient.verify(
                        request.username(),
                        request.password(),
                        correlationId)
                .map(account -> {
                    String accessToken = jwt.issueAccessToken(
                            account.accountId(),
                            account.role(),
                            account.staffId(),
                            account.departmentId(),
                            account.patientId());
                    String refreshToken = jwt.issueRefreshToken(
                            account.accountId(),
                            account.role(),
                            account.staffId(),
                            account.departmentId(),
                            account.patientId());
                    return ResponseEntity.ok((Object) new LoginResponse(
                            accessToken,
                            refreshToken,
                            account.role()));
                })
                .onErrorResume(InvalidCredentialsException.class, exception ->
                        Mono.just(errorResponse(
                                HttpStatus.UNAUTHORIZED,
                                "AUTH_INVALID_CREDENTIALS",
                                "Tên đăng nhập hoặc mật khẩu không đúng",
                                correlationId)))
                .onErrorResume(UpstreamTimeoutException.class, exception ->
                        Mono.just(errorResponse(
                                HttpStatus.GATEWAY_TIMEOUT,
                                "AUTH_UPSTREAM_TIMEOUT",
                                "Organization Service phản hồi quá thời gian",
                                correlationId)))
                .onErrorResume(UpstreamUnavailableException.class, exception ->
                        Mono.just(errorResponse(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "AUTH_UPSTREAM_UNAVAILABLE",
                                "Organization Service hiện không khả dụng",
                                correlationId)));
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<Object>> refresh(
            @Valid @RequestBody RefreshRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(
                    value = JwtClaims.HEADER_CORRELATION_ID,
                    required = false) String requestedCorrelationId) {

        String correlationId = OrganizationAuthClient.normalizeCorrelationId(
                requestedCorrelationId);

        final UUID accountId;
        try {
            Claims claims = jwt.parse(request.refreshToken());
            if (!JwtClaims.REFRESH_TOKEN_TYPE.equals(
                    claims.get(JwtClaims.TYPE, String.class))
                    || claims.getExpiration() == null
                    || !claims.getExpiration().toInstant().isAfter(Instant.now())) {
                throw new IllegalArgumentException("Invalid refresh token");
            }
            accountId = UUID.fromString(claims.getSubject());
        } catch (Exception exception) {
            return Mono.just(invalidRefreshResponse(correlationId));
        }

        return organizationAuthClient.lookupCurrentIdentity(accountId, correlationId)
                .map(identity -> {
                    if (!identity.exists() || !identity.active()) {
                        return invalidRefreshResponse(correlationId);
                    }
                    if (!accountId.equals(identity.accountId())) {
                        return errorResponse(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "AUTH_UPSTREAM_UNAVAILABLE",
                                "Organization Service hiện không khả dụng",
                                correlationId);
                    }
                    String accessToken = jwt.issueAccessToken(
                            accountId,
                            identity.role(),
                            identity.staffId(),
                            identity.departmentId(),
                            identity.patientId());
                    return ResponseEntity.ok((Object) new RefreshResponse(accessToken));
                })
                .onErrorResume(UpstreamTimeoutException.class, exception -> Mono.just(errorResponse(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "AUTH_UPSTREAM_TIMEOUT",
                        "Organization Service phản hồi quá thời gian",
                        correlationId)))
                .onErrorResume(UpstreamUnavailableException.class, exception -> Mono.just(errorResponse(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "AUTH_UPSTREAM_UNAVAILABLE",
                        "Organization Service hiện không khả dụng",
                        correlationId)));
    }

    private ResponseEntity<Object> invalidRefreshResponse(String correlationId) {
        return errorResponse(
                HttpStatus.UNAUTHORIZED,
                "AUTH_INVALID_REFRESH_TOKEN",
                "Refresh token không hợp lệ hoặc đã hết hạn",
                correlationId);
    }

    private ResponseEntity<Object> errorResponse(
            HttpStatus status,
            String code,
            String message,
            String correlationId) {
        return ResponseEntity.status(status).body(ApiResponse.fail(
                ApiResponse.ApiError.of(code, message),
                correlationId));
    }

}
