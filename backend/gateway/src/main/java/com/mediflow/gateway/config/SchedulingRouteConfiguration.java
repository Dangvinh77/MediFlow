package com.mediflow.gateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Human ingress only; internal lease/authority APIs use service discovery directly. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "mediflow.gateway.scheduling-v2", name = "enabled", havingValue = "true")
public class SchedulingRouteConfiguration {
    @Bean
    RouteLocator schedulingRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("scheduling-core", route -> route.path(
                        "/api/v2/org/consultation-rooms",
                        "/api/v2/org/departments/*/head-appointments").uri("lb://core-service"))
                .route("scheduling-clinical", route -> route.path(
                        "/api/v2/doctor-work-shifts", "/api/v2/doctor-work-shifts/*",
                        "/api/v2/doctor-work-shifts/*/cancel", "/api/v2/doctor-work-shifts/*/consultation-sessions",
                        "/api/v2/consultation-sessions", "/api/v2/consultation-sessions/*",
                        "/api/v2/consultation-sessions/*/status", "/api/v2/consultation-sessions/*/registrations",
                        "/api/v2/outpatient-registrations", "/api/v2/outpatient-registrations/*",
                        "/api/v2/outpatient-registrations/*/arrival", "/api/v2/outpatient-registrations/*/cancellation",
                        "/api/v2/records/*/follow-up-recommendations", "/api/v2/records/*/follow-up-recommendation",
                        "/api/v2/outpatient-registrations/*/room-queue-ticket", "/api/v2/outpatient-registrations/*/vital-signs",
                        "/api/v2/outpatient-registrations/*/reschedule", "/api/v2/clinic-queue",
                        "/api/v2/clinic-queue-tickets/*/call", "/api/v2/clinic-queue-tickets/*/adjustments",
                        "/api/v2/consultation-sessions/*/impact", "/api/v2/doctor-work-shifts/*/impact")
                        .uri("lb://clinical-service"))
                .route("scheduling-billing", route -> route.path(
                        "/api/v2/billing/payment-requests",
                        "/api/v2/billing/registration-settlements",
                        "/api/v2/billing/payment-requests/by-registration",
                        "/api/v2/billing/payment-requests/*/payments", "/api/v2/billing/payment-operations/*",
                        "/api/v2/billing/payment-requests/*/quote",
                        "/api/v2/billing/payment-requests/*/lab-payments",
                        "/api/v2/billing/payment-requests/*/zero-payable-eligibility",
                        "/api/v2/billing/payment-requests/*/lab-zero-payable-eligibility")
                        .uri("lb://billing-service"))
                .build();
    }
}
