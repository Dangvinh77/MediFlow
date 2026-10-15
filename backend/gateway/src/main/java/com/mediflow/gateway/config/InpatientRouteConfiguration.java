package com.mediflow.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Keeps the existing Inpatient route available to compatibility deployments only. */
@Configuration(proxyBeanMethods = false)
@Profile("!outpatient-mvp")
public class InpatientRouteConfiguration {

    @Bean
    RouteLocator inpatientRoutes(RouteLocatorBuilder routes) {
        return routes.routes()
                .route("inpatient-service", route -> route.path("/api/v1/inpatient/**")
                        .uri("lb://inpatient-service"))
                .build();
    }
}
