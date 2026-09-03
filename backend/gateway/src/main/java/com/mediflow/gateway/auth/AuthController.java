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

/**
 * STUB authentication for MediFlow. Issues JWTs for a small demo user directory so the
 * end-to-end flow works. REPLACE the credential check with a real user store / auth service
 * before any non-dev use. See docs/ai/07-security-rbac.md.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    // demo users: username -> {password, role}. Placeholder only.
    private static final Map<String, String[]> DEMO_USERS = Map.of(
            "admin", new String[]{"admin123", Roles.ADMIN},
            "doctor", new String[]{"doctor123", Roles.DOCTOR},
            "nurse", new String[]{"nurse123", Roles.NURSE},
            "pharmacist", new String[]{"pharmacist123", Roles.PHARMACIST}
    );

    private final JwtTokenService jwt;

    public AuthController(JwtTokenService jwt) {
        this.jwt = jwt;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        String[] entry = DEMO_USERS.get(req.username());
        if (entry == null || !entry[0].equals(req.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "INVALID_CREDENTIALS"));
        }
        String role = entry[1];
        String access = jwt.issueAccessToken(req.username(), role);
        String refresh = jwt.issueRefreshToken(req.username(), role);
        return ResponseEntity.ok(new LoginResponse(access, refresh, role));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest req) {
        try {
            Claims claims = jwt.parse(req.refreshToken());
            String access = jwt.issueAccessToken(
                    claims.getSubject(), claims.get(JwtClaims.ROLE, String.class));
            return ResponseEntity.ok(new RefreshResponse(access));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "INVALID_REFRESH_TOKEN"));
        }
    }
}
