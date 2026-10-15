package com.mediflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes",
                "spring.cloud.discovery.enabled=false",
                "eureka.client.enabled=false",
                "spring.cloud.gateway.discovery.locator.enabled=false",
                "mediflow.routes.surgery.enabled=true"
        })
@ActiveProfiles("outpatient-mvp")
class GatewayOutpatientMvpRouteTest {

    @Autowired
    private RouteLocator routes;

    @Test
    void outpatientProfile_hasClinicalAndCoreButNoInpatientOrSurgeryRoutes() {
        var ids = routes.getRoutes().map(route -> route.getId()).collectList().block();

        assertThat(ids).contains("clinical-service-records", "core-organization", "core-patient")
                .doesNotContain("inpatient-service", "surgery-service");
    }
}
