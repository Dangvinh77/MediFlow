package com.mediflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class RecordAccessRouteConfigurationTest {
    private static final Map<String, String> PUBLIC_ROUTES = Map.of(
            "/api/v2/records/access-declarations", "lb://clinical-service",
            "/api/v2/records/123/capabilities", "lb://clinical-service",
            "/api/v2/org/staff/123/capability-grants", "lb://core-service",
            "/api/v2/org/capability-grants/123/revocation", "lb://core-service");
    private static final List<String> INTERNAL_APIS = List.of(
            "/api/v2/clinical/patients/123/read-decision",
            "/api/v2/org/lab-release-authority");

    private static List<String> matching(RouteLocator routes, String path) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path));
        return routes.getRoutes().filterWhen(route -> route.getPredicate().apply(exchange))
                .map(route -> route.getUri().toString()).collectList().block();
    }

    private static void assertRouted(RouteLocator routes) {
        PUBLIC_ROUTES.forEach((path, uri) -> assertThat(matching(routes, path)).as(path).containsExactly(uri));
        INTERNAL_APIS.forEach(path -> assertThat(matching(routes, path)).as(path).isEmpty());
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
            "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes",
            "spring.cloud.discovery.enabled=false", "eureka.client.enabled=false"})
    class SchedulingIngressOff {
        @Autowired RouteLocator routes;

        @Test
        void recordAccessIsRoutedWithoutSchedulingIngress() {
            assertRouted(routes);
            assertThat(matching(routes, "/api/v2/consultation-sessions")).isEmpty();
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
            "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes",
            "spring.cloud.discovery.enabled=false", "eureka.client.enabled=false",
            "mediflow.gateway.scheduling-v2.enabled=true"})
    class SchedulingIngressOn {
        @Autowired RouteLocator routes;

        @Test
        void recordAccessHasExactlyOneRoute() {
            assertRouted(routes);
        }
    }
}
