package com.mediflow.gateway.security;

import com.mediflow.common.security.JwtClaims;
import com.mediflow.common.security.Roles;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    private static final String TEST_SECRET =
            "test-secret-must-have-at-least-32-bytes";

    private final JwtTokenService tokenService =
            new JwtTokenService(
                    new JwtProperties(
                            TEST_SECRET,
                            30,
                            1440));

    @Test
    void issueAccessToken_usesUserUuidAsSubject() {
        UUID userId = UUID.randomUUID();

        String token = tokenService.issueAccessToken(
                userId,
                Roles.DOCTOR);

        Claims claims = tokenService.parse(token);

        assertThat(claims.getSubject())
                .isEqualTo(userId.toString());

        assertThat(claims.get(
                JwtClaims.ROLE,
                String.class))
                .isEqualTo(Roles.DOCTOR);

        assertThat(claims.get(
                JwtClaims.CORRELATION_ID,
                String.class))
                .isNotBlank();

        assertThat(claims.getExpiration())
                .isAfter(claims.getIssuedAt());
    }

    @Test
    void issueRefreshToken_usesUserUuidAsSubject() {
        UUID userId = UUID.randomUUID();

        String token = tokenService.issueRefreshToken(
                userId,
                Roles.ADMIN);

        Claims claims = tokenService.parse(token);

        assertThat(claims.getSubject())
                .isEqualTo(userId.toString());

        assertThat(claims.get(
                JwtClaims.ROLE,
                String.class))
                .isEqualTo(Roles.ADMIN);
    }
}
