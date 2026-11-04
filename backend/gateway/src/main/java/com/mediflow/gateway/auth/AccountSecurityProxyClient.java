package com.mediflow.gateway.auth;

import com.mediflow.common.security.Roles;
import com.mediflow.gateway.security.JwtTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/** Gateway-only client for Core's service-token-protected account-security API. */
@Service
public class AccountSecurityProxyClient {
    private static final String CORE_BASE = "http://core-service/api/v1/org/account-security";
    private final WebClient client;
    private final JwtTokenService jwt;

    @Autowired
    public AccountSecurityProxyClient(WebClient.Builder builder, JwtTokenService jwt) {
        this.client = builder.build();
        this.jwt = jwt;
    }

    public Mono<ProxyResponse> forgotPassword(Object request, String correlationId) {
        return send(HttpMethod.POST, CORE_BASE + "/forgot-password", request, correlationId);
    }

    public Mono<ProxyResponse> resetPassword(Object request, String correlationId) {
        return send(HttpMethod.POST, CORE_BASE + "/reset-password", request, correlationId);
    }

    public Mono<ProxyResponse> mailboxConfig(String correlationId) {
        return send(HttpMethod.GET, CORE_BASE + "/demo-mailbox/config", null, correlationId);
    }

    public Mono<ProxyResponse> mailboxAccounts(String role, String keyword, Integer page, Integer size,
            String correlationId) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(CORE_BASE + "/demo-mailbox/accounts");
        if (role != null && !role.isBlank()) uri.queryParam("role", role);
        if (keyword != null && !keyword.isBlank()) uri.queryParam("keyword", keyword);
        if (page != null) uri.queryParam("page", page);
        if (size != null) uri.queryParam("size", size);
        return send(HttpMethod.GET, uri.build().encode(StandardCharsets.UTF_8).toUri(), null, correlationId);
    }

    public Mono<ProxyResponse> mailboxMessages(UUID accountId, Integer page, Integer size, String correlationId) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(CORE_BASE
                + "/demo-mailbox/accounts/" + accountId + "/messages");
        if (page != null) uri.queryParam("page", page);
        if (size != null) uri.queryParam("size", size);
        return send(HttpMethod.GET, uri.build().encode(StandardCharsets.UTF_8).toUri(), null, correlationId);
    }

    public Mono<ProxyResponse> mailboxMessage(UUID accountId, UUID messageId, String correlationId) {
        String path = CORE_BASE + "/demo-mailbox/accounts/" + accountId + "/messages/" + messageId;
        return send(HttpMethod.GET, path, null, correlationId);
    }

    private Mono<ProxyResponse> send(HttpMethod method, String uri, Object request, String correlationId) {
        return send(method, URI.create(uri), request, correlationId);
    }

    private Mono<ProxyResponse> send(HttpMethod method, URI uri, Object request, String correlationId) {
        String serviceToken = jwt.issueServiceToken("gateway", Roles.SYSTEM, correlationId);
        WebClient.RequestBodySpec spec = client.method(method).uri(uri).headers(headers -> {
            headers.setBearerAuth(serviceToken);
            headers.set("X-Correlation-Id", correlationId);
            headers.setContentType(MediaType.APPLICATION_JSON);
        });
        Mono<ProxyResponse> response = (request == null ? spec.exchangeToMono(reply ->
                reply.bodyToMono(byte[].class).defaultIfEmpty(new byte[0]).map(body -> new ProxyResponse(
                        reply.statusCode().value(), reply.headers().contentType().orElse(MediaType.APPLICATION_JSON), body)))
                : spec.bodyValue(request).exchangeToMono(reply ->
                        reply.bodyToMono(byte[].class).defaultIfEmpty(new byte[0]).map(body -> new ProxyResponse(
                                reply.statusCode().value(), reply.headers().contentType().orElse(MediaType.APPLICATION_JSON), body))));
        return response.timeout(Duration.ofSeconds(4)).onErrorMap(failure ->
                failure instanceof JwtTokenService.UpstreamUnavailableException ? failure
                        : new JwtTokenService.UpstreamUnavailableException(
                                failure instanceof TimeoutException
                                        ? "Core account-security request timed out"
                                        : "Core account-security request failed", failure));
    }

    public record ProxyResponse(int status, MediaType contentType, byte[] body) { }
}
