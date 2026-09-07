package com.mediflow.gateway.auth;

import com.mediflow.common.security.JwtClaims;
import com.mediflow.common.security.Roles;
import com.mediflow.gateway.auth.AuthDtos.LoginRequest;
import com.mediflow.gateway.auth.AuthDtos.LoginResponse;
import com.mediflow.gateway.auth.AuthDtos.RefreshRequest;
import com.mediflow.gateway.auth.AuthDtos.RefreshResponse;
import com.mediflow.gateway.security.JwtTokenService;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Stub authentication for local development.
 *
 * <p>Every demo user has a stable UUID so downstream services can
 * safely use the JWT subject for ownership checks.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Map<String, DemoUser> DEMO_USERS = Map.of(
            "admin",
            new DemoUser(
                    UUID.fromString(
                            "00000000-0000-0000-0000-000000000001"),
                    "admin123",
                    Roles.ADMIN),
            "doctor",
            new DemoUser(
                    UUID.fromString(
                            "00000000-0000-0000-0000-000000000002"),
                    "doctor123",
                    Roles.DOCTOR),
            "nurse",
            new DemoUser(
                    UUID.fromString(
                            "00000000-0000-0000-0000-000000000003"),
                    "nurse123",
                    Roles.NURSE),
            "pharmacist",
            new DemoUser(
                    UUID.fromString(
                            "00000000-0000-0000-0000-000000000004"),
                    "pharmacist123",
                    Roles.PHARMACIST)
    );

    private final JwtTokenService jwt;

    public AuthController(JwtTokenService jwt) {
        this.jwt = jwt;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request) {

        DemoUser user = DEMO_USERS.get(request.username());

        if (user == null
                || !user.password().equals(request.password())) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error",
                            "INVALID_CREDENTIALS"));
        }

        String accessToken =
                jwt.issueAccessToken(user.userId(), user.role());

        String refreshToken =
                jwt.issueRefreshToken(user.userId(), user.role());

        return ResponseEntity.ok(
                new LoginResponse(
                        accessToken,
                        refreshToken,
                        user.role()));
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

            String accessToken =
                    jwt.issueAccessToken(userId, role);

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

    private record DemoUser(
            UUID userId,
            String password,
            String role) {
    }
}