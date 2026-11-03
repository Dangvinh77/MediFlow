package com.mediflow.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounds public recovery traffic by the socket peer; forwarded client headers are never trusted. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class PublicAuthRateLimitWebFilter implements WebFilter {
    private static final int MAX_TRACKED_ORIGINS = 10_000;
    private final GatewayErrorResponseWriter errors;
    private final int maxRequests;
    private final long windowNanos;
    private final Map<String, Window> windows = new LinkedHashMap<>(128, 0.75f, true);

    public PublicAuthRateLimitWebFilter(GatewayErrorResponseWriter errors,
            @Value("${mediflow.security.public-rate-limit.max-requests:5}") int maxRequests,
            @Value("${mediflow.security.public-rate-limit.window-seconds:900}") long windowSeconds) {
        this.errors = errors;
        this.maxRequests = Math.max(1, maxRequests);
        this.windowNanos = Duration.ofSeconds(Math.max(1, windowSeconds)).toNanos();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!PublicEndpointPolicy.isPublicAuthMutation(
                exchange.getRequest().getPath().value(), exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }
        String origin = socketAddress(exchange.getRequest().getRemoteAddress());
        if (tryAcquire(origin, System.nanoTime())) return chain.filter(exchange);
        return errors.write(exchange, HttpStatus.TOO_MANY_REQUESTS,
                "AUTH_RATE_LIMITED", "Quá nhiều yêu cầu. Vui lòng thử lại sau.");
    }

    private synchronized boolean tryAcquire(String origin, long now) {
        Window current = windows.get(origin);
        if (current == null || now - current.startedAtNanos() >= windowNanos) {
            if (current == null && windows.size() >= MAX_TRACKED_ORIGINS) {
                Iterator<String> oldest = windows.keySet().iterator();
                if (oldest.hasNext()) {
                    oldest.next();
                    oldest.remove();
                }
            }
            windows.put(origin, new Window(now, 1));
            return true;
        }
        if (current.requests() >= maxRequests) return false;
        windows.put(origin, new Window(current.startedAtNanos(), current.requests() + 1));
        return true;
    }

    private static String socketAddress(InetSocketAddress address) {
        if (address == null) return "unknown";
        if (address.getAddress() != null) return address.getAddress().getHostAddress();
        String host = address.getHostString();
        return host == null || host.isBlank() ? "unknown" : host.toLowerCase(java.util.Locale.ROOT);
    }

    private record Window(long startedAtNanos, int requests) { }
}
