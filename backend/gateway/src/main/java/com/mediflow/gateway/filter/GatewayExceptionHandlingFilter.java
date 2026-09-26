package com.mediflow.gateway.filter;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

/** Converts transport failures from routed services to stable Gateway errors. */
@Component
public class GatewayExceptionHandlingFilter implements GlobalFilter, Ordered {

    private final GatewayErrorResponseWriter errors;

    public GatewayExceptionHandlingFilter(GatewayErrorResponseWriter errors) {
        this.errors = errors;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange)
                .onErrorResume(exception -> {
                    Throwable cause = rootCause(exception);
                    if (isTimeout(cause)) {
                        return errors.write(exchange, HttpStatus.GATEWAY_TIMEOUT,
                                "GATEWAY_TIMEOUT", "Downstream service timed out");
                    }
                    if (cause instanceof ConnectException
                            || cause instanceof UnknownHostException
                            || cause instanceof CallNotPermittedException) {
                        return errors.write(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                                "GATEWAY_UPSTREAM_UNAVAILABLE",
                                "Downstream service is unavailable");
                    }
                    // A routed service that cannot be resolved or contacted must never leak
                    // Spring Cloud's implementation-specific error body to the client.
                    if (exchange.getRequest().getPath().value().startsWith("/api/v1/")) {
                        return errors.write(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                                "GATEWAY_UPSTREAM_UNAVAILABLE",
                                "Downstream service is unavailable");
                    }
                    return Mono.error(exception);
                });
    }

    private boolean isTimeout(Throwable cause) {
        return cause instanceof TimeoutException
                || cause.getClass().getSimpleName().contains("Timeout");
    }

    private Throwable rootCause(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current != current.getCause()) {
            current = current.getCause();
        }
        return current;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
