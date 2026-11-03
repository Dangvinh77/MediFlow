package com.mediflow.gateway.auth;

import com.mediflow.common.security.JwtClaims;
import com.mediflow.common.security.Roles;
import com.mediflow.gateway.security.JwtProperties;
import com.mediflow.gateway.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AccountSecurityProxyClientTest {
    private static final String SECRET = "test-secret-must-have-at-least-32-bytes";

    @Test
    void sendsOnlyShortLivedGatewayServiceTokenToInternalCorePath() {
        var captured = new AtomicReference<org.springframework.web.reactive.function.client.ClientRequest>();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            captured.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"success\":true,\"data\":{\"accepted\":true}}")
                    .build());
        });
        var jwt = new JwtTokenService(new JwtProperties(SECRET, 30, 1440, 1));
        var client = new AccountSecurityProxyClient(builder, jwt);
        String correlation = UUID.randomUUID().toString();

        var response = client.forgotPassword(new AccountSecurityProxyController.ForgotPasswordRequest(
                "seed@example.test"), correlation).block();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(200);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).contains("\"accepted\":true");
        var request = captured.get();
        assertThat(request.url().getPath()).isEqualTo("/api/v1/org/account-security/forgot-password");
        assertThat(request.headers().getFirst("X-Correlation-Id")).isEqualTo(correlation);
        String authorization = request.headers().getFirst("Authorization");
        assertThat(authorization).startsWith("Bearer ");
        var claims = jwt.parse(authorization.substring("Bearer ".length()));
        assertThat(claims.getSubject()).isEqualTo("gateway");
        assertThat(claims.get(JwtClaims.ROLE, String.class)).isEqualTo(Roles.SYSTEM);
        assertThat(claims.get(JwtClaims.TYPE, String.class)).isEqualTo(JwtClaims.SERVICE_TOKEN_TYPE);
        assertThat(claims.getExpiration().toInstant()).isBeforeOrEqualTo(
                claims.getIssuedAt().toInstant().plusSeconds(60));
    }

    @Test
    void mailboxSearchQueryIsEncodedOnceAndPreservesVietnameseAndEmailCharacters() {
        var captured = new AtomicReference<org.springframework.web.reactive.function.client.ClientRequest>();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            captured.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"success\":true,\"data\":{}}")
                    .build());
        });
        var jwt = new JwtTokenService(new JwtProperties(SECRET, 30, 1440, 1));
        var client = new AccountSecurityProxyClient(builder, jwt);

        client.mailboxAccounts("PATIENT", "Nguyễn Văn A@example.test", 2, 20,
                UUID.randomUUID().toString()).block();

        String rawQuery = captured.get().url().getRawQuery();
        assertThat(rawQuery).doesNotContain("%25");
        assertThat(URLDecoder.decode(rawQuery, StandardCharsets.UTF_8))
                .isEqualTo("role=PATIENT&keyword=Nguyễn Văn A@example.test&page=2&size=20");
    }
}
