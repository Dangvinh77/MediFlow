package com.mediflow.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PublicAuthRateLimitWebFilterTest {
    @Test
    void limitsRecoveryBySocketPeerAndIgnoresForwardedFor() {
        var filter = new PublicAuthRateLimitWebFilter(
                new GatewayErrorResponseWriter(new ObjectMapper().findAndRegisterModules()), 5, 900);
        var forwarded = new AtomicInteger();

        for (int attempt = 0; attempt < 5; attempt++) {
            var exchange = MockServerWebExchange.from(MockServerHttpRequest
                    .method(HttpMethod.POST, "/api/v1/auth/forgot-password")
                    .header("X-Forwarded-For", "198.51.100." + (attempt + 1)));
            filter.filter(exchange, ignored -> { forwarded.incrementAndGet(); return Mono.empty(); }).block();
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }

        var sixth = MockServerWebExchange.from(MockServerHttpRequest
                .method(HttpMethod.POST, "/api/v1/auth/reset-password")
                .header("Forwarded", "for=203.0.113.99")
                .header("X-Forwarded-For", "203.0.113.99"));
        filter.filter(sixth, ignored -> { forwarded.incrementAndGet(); return Mono.empty(); }).block();

        assertThat(forwarded.get()).isEqualTo(5);
        assertThat(sixth.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void normalLoginAndNonPublicPathsDoNotConsumeRecoveryBucket() {
        var filter = new PublicAuthRateLimitWebFilter(
                new GatewayErrorResponseWriter(new ObjectMapper().findAndRegisterModules()), 1, 900);
        var forwarded = new AtomicInteger();
        for (String path : new String[] {"/api/v1/auth/login", "/api/v1/org/accounts"}) {
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.POST, path));
            filter.filter(exchange, ignored -> { forwarded.incrementAndGet(); return Mono.empty(); }).block();
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }
        var recovery = MockServerWebExchange.from(MockServerHttpRequest
                .method(HttpMethod.POST, "/api/v1/auth/forgot-password"));
        filter.filter(recovery, ignored -> { forwarded.incrementAndGet(); return Mono.empty(); }).block();
        assertThat(recovery.getResponse().getStatusCode()).isNull();
        assertThat(forwarded.get()).isEqualTo(3);
    }
}
