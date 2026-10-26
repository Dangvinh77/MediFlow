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
    void schedulingV2IsNotPublishedByDefault() {
        assertThat(routeLocator.getRoutes()
                .filter(route -> route.getId().startsWith("scheduling-"))
                .collectList().block()).isEmpty();
    }

    @Test
    void supportRoutesKeepPublicPathsAndTargetTheSharedHost() {
        for (String context : new String[]{"report", "notification"}) {
            Route route = routeLocator.getRoutes()
                    .filter(candidate -> (context + "-service").equals(candidate.getId())).next().block();
            assertThat(route).isNotNull();
            assertThat(route.getUri()).isEqualTo(URI.create("lb://support-service"));
            assertThat(route.getFilters()).isEmpty();
            String path = context.equals("report") ? "/api/v1/reports/daily" : "/api/v1/notifications/123";
            assertThat(Mono.from(route.getPredicate().apply(MockServerWebExchange.from(
                    MockServerHttpRequest.get(path).build()))).block()).isTrue();
        }
    }

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
    void clinicalRecordsRoute_forwardsTheLabOrderCreatePath() {
        Route route = routeLocator.getRoutes()
                .filter(candidate -> "clinical-service-records".equals(candidate.getId()))
                .next()
                .block();

        assertThat(route).isNotNull();
        assertThat(route.getUri()).isEqualTo(URI.create("lb://clinical-service"));
        assertThat(Mono.from(route.getPredicate().apply(MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/records/00000000-0000-0000-0000-000000000001/lab-orders")
                        .build()))).block()).isTrue();
    }

    @Test
    void internalLabOrderContext_hasNoPublicGatewayRoute() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(
                "/api/v1/lab-orders/00000000-0000-0000-0000-000000000001/context").build());

        assertThat(routeLocator.getRoutes()
                .filter(route -> Boolean.TRUE.equals(Mono.from(route.getPredicate().apply(exchange)).block()))
                .collectList().block()).isEmpty();
    }

}
