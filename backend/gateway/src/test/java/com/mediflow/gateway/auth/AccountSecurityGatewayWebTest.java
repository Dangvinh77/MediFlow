package com.mediflow.gateway.auth;

import com.mediflow.gateway.filter.AccountSessionValidationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes",
        "mediflow.demo-mailbox.enabled=true",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.cloud.gateway.discovery.locator.enabled=false"
})
@AutoConfigureWebTestClient
class AccountSecurityGatewayWebTest {
    @Autowired private WebTestClient web;
    @MockBean private AccountSecurityProxyClient core;
    @MockBean private AccountSessionValidationFilter sessionValidation;

    @org.junit.jupiter.api.BeforeEach
    void passSessionGuardForRoutedFixtures() {
        when(sessionValidation.filter(any(), any())).thenAnswer(call ->
                ((org.springframework.cloud.gateway.filter.GatewayFilterChain) call.getArgument(1))
                        .filter(call.getArgument(0)));
    }

    @Test
    void publicForgotPasswordAcceptsExpiredBearerAndUsesCoreResponseEnvelope() {
        when(core.forgotPassword(any(), anyString())).thenReturn(Mono.just(json(
                "{\"success\":true,\"data\":{\"accepted\":true},\"error\":null}")));

        web.post().uri("/api/v1/auth/forgot-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer expired-token-from-browser-session")
                .header("X-Forwarded-For", "203.0.113.50")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"seed@example.test\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.accepted").isEqualTo(true);
    }

    @Test
    void demoMailboxPublicContractForwardsQueryAndFullDetail() {
        String accountId = "2a6d16a1-8cbb-48bd-893d-84ba9876c3d2";
        String messageId = "904a65b8-028b-466a-8cb0-9d209c9dad8c";
        when(core.mailboxConfig(anyString())).thenReturn(Mono.just(json(
                "{\"success\":true,\"data\":{\"enabled\":true}}")));
        when(core.mailboxAccounts(org.mockito.ArgumentMatchers.eq("PATIENT"),
                org.mockito.ArgumentMatchers.eq("seed"), org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(20), anyString())).thenReturn(Mono.just(json(
                "{\"success\":true,\"data\":{\"content\":[],\"totalElements\":0,\"totalPages\":0,\"number\":0,\"size\":20}}")));
        when(core.mailboxMessage(org.mockito.ArgumentMatchers.any(java.util.UUID.class),
                org.mockito.ArgumentMatchers.any(java.util.UUID.class), anyString())).thenReturn(Mono.just(json(
                "{\"success\":true,\"data\":{\"messageId\":\"" + messageId
                        + "\",\"subject\":\"Welcome\",\"from\":\"MediFlow\",\"to\":\"seed@example.test\","
                        + "\"createdAt\":\"2026-11-04T10:00:00Z\",\"textBody\":\"hello\",\"htmlBody\":\"<b>hello</b>\"}}")));

        web.get().uri("/api/v1/demo/mailbox/config").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.data.enabled").isEqualTo(true);
        web.get().uri("/api/v1/demo/mailbox/accounts?role=PATIENT&keyword=seed&page=0&size=20").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.data.size").isEqualTo(20);
        web.get().uri("/api/v1/demo/mailbox/accounts/{accountId}/messages/{messageId}", accountId, messageId)
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.data.htmlBody").isEqualTo("<b>hello</b>")
                .jsonPath("$.data.textBody").isEqualTo("hello");
    }

    private static AccountSecurityProxyClient.ProxyResponse json(String body) {
        return new AccountSecurityProxyClient.ProxyResponse(200, MediaType.APPLICATION_JSON,
                body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
