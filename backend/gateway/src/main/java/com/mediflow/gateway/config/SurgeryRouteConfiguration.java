package com.mediflow.gateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Route activation is independent of Surgery's downstream business feature gate. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "mediflow.routes.surgery", name = "enabled", havingValue = "true")
public class SurgeryRouteConfiguration {

    @Bean
    RouteLocator surgeryRoutes(RouteLocatorBuilder routes) {
        return routes.routes()
                .route("surgery-service", route -> route.path("/api/v1/surgery/**")
                        .uri("lb://surgery-service"))
                .build();
    }
}
