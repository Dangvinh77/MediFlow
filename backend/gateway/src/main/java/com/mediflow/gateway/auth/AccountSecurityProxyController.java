package com.mediflow.gateway.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.gateway.filter.CorrelationIdWebFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Public account recovery and gated demo-mailbox facade; Core remains the data authority. */
@RestController
@RequestMapping("/api/v1")
public class AccountSecurityProxyController {
    private final AccountSecurityProxyClient core;
    private final ObjectMapper mapper;
    private final boolean demoMailboxEnabled;

    public AccountSecurityProxyController(AccountSecurityProxyClient core, ObjectMapper mapper,
            @Value("${mediflow.demo-mailbox.enabled:false}") boolean demoMailboxEnabled) {
        this.core = core;
        this.mapper = mapper;
        this.demoMailboxEnabled = demoMailboxEnabled;
    }

    public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 254) String email) { }
    public record ResetPasswordRequest(@NotBlank @Size(max = 256) String token,
            @NotBlank @Size(min = 8, max = 72) String newPassword) { }
    public record DemoMailboxConfigResponse(boolean enabled) { }

    @PostMapping("/auth/forgot-password")
    public Mono<ResponseEntity<byte[]>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
            ServerWebExchange exchange) {
        return core.forgotPassword(request, correlationId(exchange))
                .map(response -> upstreamResponse(response, correlationId(exchange)));
    }

    @PostMapping("/auth/reset-password")
    public Mono<ResponseEntity<byte[]>> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
            ServerWebExchange exchange) {
        return core.resetPassword(request, correlationId(exchange))
                .map(response -> upstreamResponse(response, correlationId(exchange)));
    }

    @GetMapping("/demo/mailbox/config")
    public Mono<ResponseEntity<byte[]>> mailboxConfig(ServerWebExchange exchange) {
        String correlationId = correlationId(exchange);
        if (!demoMailboxEnabled) {
            return Mono.just(localResponse(HttpStatus.OK,
                    ApiResponse.ok(new DemoMailboxConfigResponse(false), correlationId), correlationId));
        }
        return core.mailboxConfig(correlationId).map(response -> upstreamResponse(response, correlationId));
    }

    @GetMapping("/demo/mailbox/accounts")
    public Mono<ResponseEntity<byte[]>> mailboxAccounts(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) @Size(max = 120) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            ServerWebExchange exchange) {
        if (!demoMailboxEnabled) return Mono.just(mailboxDisabled(exchange));
        String correlationId = correlationId(exchange);
        return core.mailboxAccounts(role, keyword, page, size, correlationId)
                .map(response -> upstreamResponse(response, correlationId));
    }

    @GetMapping("/demo/mailbox/accounts/{accountId}/messages")
    public Mono<ResponseEntity<byte[]>> mailboxMessages(@PathVariable UUID accountId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, ServerWebExchange exchange) {
        if (!demoMailboxEnabled) return Mono.just(mailboxDisabled(exchange));
        String correlationId = correlationId(exchange);
        return core.mailboxMessages(accountId, page, size, correlationId)
                .map(response -> upstreamResponse(response, correlationId));
    }

    @GetMapping("/demo/mailbox/accounts/{accountId}/messages/{messageId}")
    public Mono<ResponseEntity<byte[]>> mailboxMessage(@PathVariable UUID accountId,
            @PathVariable UUID messageId, ServerWebExchange exchange) {
        if (!demoMailboxEnabled) return Mono.just(mailboxDisabled(exchange));
        String correlationId = correlationId(exchange);
        return core.mailboxMessage(accountId, messageId, correlationId)
                .map(response -> upstreamResponse(response, correlationId));
    }

    private ResponseEntity<byte[]> mailboxDisabled(ServerWebExchange exchange) {
        String correlationId = correlationId(exchange);
        ApiResponse<Void> body = ApiResponse.fail(
                ApiResponse.ApiError.of("DEMO_MAILBOX_DISABLED", "Demo mailbox is disabled"), correlationId);
        return localResponse(HttpStatus.NOT_FOUND, body, correlationId);
    }

    private ResponseEntity<byte[]> upstreamResponse(AccountSecurityProxyClient.ProxyResponse response,
            String correlationId) {
        return ResponseEntity.status(response.status())
                .contentType(response.contentType())
                .cacheControl(CacheControl.noStore())
                .header(JwtClaims.HEADER_CORRELATION_ID, correlationId)
                .body(response.body());
    }

    private ResponseEntity<byte[]> localResponse(HttpStatus status, Object body, String correlationId) {
        try {
            return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                    .cacheControl(CacheControl.noStore())
                    .header(JwtClaims.HEADER_CORRELATION_ID, correlationId)
                    .body(mapper.writeValueAsBytes(body));
        } catch (JsonProcessingException failure) {
            byte[] fallback = ("{\"success\":false,\"data\":null,\"error\":{\"code\":\"GATEWAY_ERROR\","
                    + "\"message\":\"Gateway error\",\"details\":[]},\"correlationId\":\""
                    + correlationId + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON)
                    .cacheControl(CacheControl.noStore())
                    .header(JwtClaims.HEADER_CORRELATION_ID, correlationId).body(fallback);
        }
    }

    private String correlationId(ServerWebExchange exchange) {
        String value = exchange.getAttribute(CorrelationIdWebFilter.ATTRIBUTE);
        return value == null ? CorrelationIdWebFilter.normalize(
                exchange.getRequest().getHeaders().getFirst(JwtClaims.HEADER_CORRELATION_ID)) : value;
    }
}
