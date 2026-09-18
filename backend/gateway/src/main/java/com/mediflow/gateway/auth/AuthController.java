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
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;
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
            @RequestBody LoginRequest request,
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
                            account.departmentId());
                    String refreshToken = jwt.issueRefreshToken(
                            account.accountId(),
                            account.role(),
                            account.departmentId());
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
                .onErrorResume(UpstreamUnavailableException.class, exception ->
                        Mono.just(errorResponse(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "AUTH_UPSTREAM_UNAVAILABLE",
                                "Organization Service hiện không khả dụng",
                                correlationId)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @RequestBody RefreshRequest request) {

        try {
            Claims claims =
                    jwt.parse(request.refreshToken());

            UUID userId =
                    UUID.fromString(claims.getSubject());

            String role =
                    claims.get(
                            JwtClaims.ROLE,
                            String.class);

            UUID departmentId = null;
            String departmentClaim = claims.get(
                    JwtTokenService.DEPARTMENT_ID_CLAIM,
                    String.class);
            if (departmentClaim != null) {
                departmentId = UUID.fromString(departmentClaim);
            }

            String accessToken =
                    jwt.issueAccessToken(userId, role, departmentId);

            return ResponseEntity.ok(
                    new RefreshResponse(accessToken));
        } catch (Exception exception) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error",
                            "INVALID_REFRESH_TOKEN"));
        }
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
