package com.mediflow.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Human ingress for record access and capability grants. Always on: clients read capabilities even
 * where scheduling ingress or the read policy is off. Internal decision APIs are deliberately absent.
 */
@Configuration(proxyBeanMethods = false)
public class RecordAccessRouteConfiguration {
    @Bean
    RouteLocator recordAccessRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("record-access-clinical", route -> route.path(
                        "/api/v2/records/access-declarations", "/api/v2/records/*/capabilities")
                        .uri("lb://clinical-service"))
                .route("record-access-core", route -> route.path(
                        "/api/v2/org/staff/*/capability-grants",
                        "/api/v2/org/capability-grants/*/revocation").uri("lb://core-service"))
                .build();
    }
}
