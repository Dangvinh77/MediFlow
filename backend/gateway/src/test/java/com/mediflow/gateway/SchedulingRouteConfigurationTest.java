package com.mediflow.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes",
        "spring.cloud.discovery.enabled=false", "eureka.client.enabled=false",
        "mediflow.gateway.scheduling-v2.enabled=true"})
class SchedulingRouteConfigurationTest {
    @Autowired RouteLocator routes;

    @Test
    void enabledIngressTargetsCorrectServicesWithoutPublishingInternalApis() {
        Map<String, String> cases = Map.of(
                "/api/v2/org/accounts/123/schedule-management-scope", "lb://core-service",
                "/api/v2/org/consultation-rooms", "lb://core-service",
                "/api/v2/consultation-sessions", "lb://clinical-service",
                "/api/v2/consultation-sessions/mine", "lb://clinical-service",
                "/api/v2/outpatient-registrations/123", "lb://clinical-service",
                "/api/v2/billing/payment-requests/by-registration", "lb://billing-service",
                "/api/v2/billing/payment-operations/123", "lb://billing-service");
        cases.forEach((path, uri) -> assertThat(matching(path)).containsExactly(uri));
        for (String path : new String[]{"/api/v2/org/scheduling-authority",
                "/api/v2/org/consultation-eligibility", "/api/v2/clinical/reservations/123/payment-locks",
                "/api/v2/clinical/reservations/123/payment-context",
                "/api/v2/clinical/reservations/123/payment-resolution",
                "/api/v2/billing/payment-operations/123/close-if-unpaid"}) {
            assertThat(matching(path)).as(path).isEmpty();
        }
    }

    private java.util.List<String> matching(String path) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path));
        return routes.getRoutes().filterWhen(route -> route.getPredicate().apply(exchange))
                .map(route -> route.getUri().toString()).collectList().block();
    }
}
