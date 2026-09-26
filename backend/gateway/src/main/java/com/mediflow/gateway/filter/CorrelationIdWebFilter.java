package com.mediflow.gateway.filter;

import com.mediflow.common.security.JwtClaims;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Establishes one correlation id before any Gateway authentication or routing
 * filter runs. Only UUID values are accepted from callers so the value is safe
 * to use in the cross-service contract.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

    public static final String ATTRIBUTE = CorrelationIdWebFilter.class.getName() + ".id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = normalize(
                exchange.getRequest().getHeaders().getFirst(JwtClaims.HEADER_CORRELATION_ID));

        ServerWebExchange mutated = exchange.mutate()
                .request(request -> request.headers(headers ->
                        headers.set(JwtClaims.HEADER_CORRELATION_ID, correlationId)))
                .build();
        mutated.getAttributes().put(ATTRIBUTE, correlationId);
        mutated.getResponse().getHeaders().set(JwtClaims.HEADER_CORRELATION_ID, correlationId);
        mutated.getResponse().beforeCommit(() -> {
            mutated.getResponse().getHeaders().set(
                    JwtClaims.HEADER_CORRELATION_ID, correlationId);
            return Mono.empty();
        });

        return chain.filter(mutated);
    }

    public static String normalize(String value) {
        if (value != null && !value.isBlank()) {
            try {
                return UUID.fromString(value).toString();
            } catch (IllegalArgumentException ignored) {
                // A malformed caller value is replaced rather than propagated.
            }
        }
        return UUID.randomUUID().toString();
    }
}
