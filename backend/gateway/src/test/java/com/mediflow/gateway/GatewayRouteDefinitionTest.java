package com.mediflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes",
                "spring.cloud.discovery.enabled=false",
                "eureka.client.enabled=false",
                "spring.cloud.gateway.discovery.locator.enabled=false"
        })
class GatewayRouteDefinitionTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void surgeryRoute_defaultFlag_hasNoRoute() {
        assertThat(routeLocator.getRoutes()
                .filter(candidate -> "surgery-service".equals(candidate.getId()))
                .collectList().block()).isEmpty();
    }

    @Test
    void inpatientRoute_hasStableIdEurekaUriAndPathPredicate() {
        Route route = routeLocator.getRoutes()
                .filter(candidate -> "inpatient-service".equals(candidate.getId()))
                .next()
                .block();

        assertThat(route).isNotNull();
        assertThat(route.getUri()).isEqualTo(URI.create("lb://inpatient-service"));
        assertThat(route.getFilters()).isEmpty();
        assertThat(Mono.from(route.getPredicate().apply(MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/inpatient/admissions").build()))).block())
                .isTrue();
    }

    @Test
    void surgeryRoute_hasStableIdEurekaUriAndPreservesPath() {
        Route route = routeLocator.getRoutes()
                .filter(candidate -> "surgery-service".equals(candidate.getId()))
                .next()
                .block();

        assertThat(route).isNotNull();
        assertThat(route.getUri()).isEqualTo(URI.create("lb://surgery-service"));
        assertThat(route.getFilters()).isEmpty();
        assertThat(Mono.from(route.getPredicate().apply(MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/surgery/cases/1/preop").build()))).block())
                .isTrue();
        assertThat(Mono.from(route.getPredicate().apply(MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/surgery/cases/1/preop").build()))).block())
                .isTrue();
    }
}
