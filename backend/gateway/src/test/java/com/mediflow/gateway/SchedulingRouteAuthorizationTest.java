package com.mediflow.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.gateway.filter.GatewayErrorResponseWriter;
import com.mediflow.gateway.filter.RouteAuthorizationFilter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.assertThat;

class SchedulingRouteAuthorizationTest {
    private final RouteAuthorizationFilter filter = new RouteAuthorizationFilter(
            new GatewayErrorResponseWriter(new ObjectMapper().findAndRegisterModules()));
    private static final List<String> ROLES = List.of("ADMIN", "NURSE", "DOCTOR",
            "CASHIER", "LAB_TECH", "PHARMACIST", "PATIENT", "SYSTEM");

    @Test
    void humanSchedulingRoutesHaveExactRolesAndMethods() {
        check("GET", "/api/v2/org/consultation-rooms", "ADMIN", "DOCTOR");
        check("POST", "/api/v2/consultation-sessions", "ADMIN", "DOCTOR");
        check("PUT", "/api/v2/consultation-sessions/123", "ADMIN", "DOCTOR");
        check("PUT", "/api/v2/consultation-sessions/123/status", "ADMIN", "DOCTOR");
        check("GET", "/api/v2/consultation-sessions", "ADMIN", "NURSE", "DOCTOR");
        check("GET", "/api/v2/consultation-sessions/mine", "DOCTOR");
        check("GET", "/api/v2/consultation-sessions/123/registrations", "ADMIN", "NURSE", "DOCTOR");
        check("POST", "/api/v2/outpatient-registrations", "ADMIN", "NURSE");
        check("GET", "/api/v2/outpatient-registrations/mine", "PATIENT");
        check("GET", "/api/v2/outpatient-registrations/follow-up-sources", "ADMIN", "NURSE");
        check("POST", "/api/v2/records/123/follow-up-recommendations", "DOCTOR");
        check("GET", "/api/v2/records/123/follow-up-recommendation", "ADMIN", "NURSE", "DOCTOR", "PATIENT");
        check("GET", "/api/v2/outpatient-registrations/123", "ADMIN", "NURSE", "DOCTOR", "PATIENT");
        check("PUT", "/api/v2/outpatient-registrations/123/arrival", "ADMIN", "NURSE");
        check("PUT", "/api/v2/outpatient-registrations/123/cancellation", "ADMIN", "NURSE", "DOCTOR");
        check("GET", "/api/v2/billing/payment-requests", "ADMIN", "CASHIER");
        check("GET", "/api/v2/billing/payment-requests/by-registration", "ADMIN", "CASHIER");
        check("POST", "/api/v2/billing/payment-requests/123/payments", "ADMIN", "CASHIER");
        check("GET", "/api/v2/billing/payment-requests/123/quote", "ADMIN", "CASHIER");
        check("POST", "/api/v2/billing/payment-requests/123/zero-payable-eligibility", "ADMIN", "CASHIER");
        check("POST", "/api/v2/billing/payment-requests/123/lab-zero-payable-eligibility", "ADMIN", "CASHIER");
        check("POST", "/api/v2/billing/payment-requests/123/lab-payments", "ADMIN", "CASHIER");
        check("GET", "/api/v2/billing/payment-operations/123", "ADMIN", "CASHIER");
    }

    @Test
    void departmentHeadAndDoctorShiftRoutesExposeOnlyTheirHumanContracts() {
        check("GET", "/api/v2/consultation-sessions/summary", "ADMIN", "DOCTOR");
        check("PUT", "/api/v2/consultation-sessions/summary");
        check("PUT", "/api/v2/consultation-sessions/mine");
        check("GET", "/api/v1/org/me/capabilities", "ADMIN", "DOCTOR", "NURSE", "PHARMACIST", "CASHIER", "LAB_TECH");
        check("GET", "/api/v2/org/departments/123/head-appointments", "ADMIN");
        check("PUT", "/api/v2/org/departments/123/head-appointments", "ADMIN");
        check("GET", "/api/v2/doctor-work-shifts", "ADMIN", "DOCTOR");
        check("POST", "/api/v2/doctor-work-shifts", "ADMIN", "DOCTOR");
        check("GET", "/api/v2/doctor-work-shifts/mine", "DOCTOR");
        check("PUT", "/api/v2/doctor-work-shifts/123", "ADMIN", "DOCTOR");
        check("PUT", "/api/v2/doctor-work-shifts/123/cancel", "ADMIN", "DOCTOR");
        check("POST", "/api/v2/doctor-work-shifts/123/consultation-sessions", "ADMIN", "DOCTOR");
        check("POST", "/api/v1/org/me/capabilities");
        check("DELETE", "/api/v2/doctor-work-shifts/123");
        check("GET", "/api/v2/doctor-work-shifts/123/cancel/extra");
        check("POST", "/api/v2/doctor-work-shifts/mine");
        check("PUT", "/api/v2/doctor-work-shifts/mine");
        check("POST", "/api/v2/org/departments/123/head-appointments");
    }

    @Test
    void internalAndUnknownRoutesRemainDeniedAtThePublicBoundary() {
        for (String path : List.of("/api/v2/org/scheduling-authority", "/api/v2/org/consultation-eligibility",
                "/api/v2/org/current-doctor-authority", "/api/v2/org/lab-release-authority",
                "/api/v2/lab/source-orders/123/lifecycle",
                "/api/v2/clinical/reservations/123/payment-locks",
                "/api/v2/clinical/reservations/123/payment-resolution",
                "/api/v2/clinical/reservations/123/payment-context",
                "/api/v2/billing/payment-operations/123/close-if-unpaid",
                "/api/v2/consultation-sessions/123/status/extra", "/api/v2/billing/payment-operations/123/extra")) {
            for (String method : List.of("GET", "POST", "PUT", "DELETE")) check(method, path);
        }
        check("DELETE", "/api/v2/consultation-sessions/123");
    }

    @Test
    void careLabelsAllowOnlyHumanReadsWithoutOpeningStaffProfiles() {
        check("GET", "/api/v1/org/care-labels/doctor/123", "ADMIN", "DOCTOR", "NURSE", "PHARMACIST", "CASHIER", "LAB_TECH", "PATIENT");
        for (String method : List.of("POST", "PUT", "DELETE", "PATCH")) check(method, "/api/v1/org/care-labels/doctor/123");
        check("GET", "/api/v1/org/care-labels/doctor/123/extra");
    }

    private void check(String method, String path, String... permitted) {
        Set<String> allowed = Set.of(permitted);
        for (String role : ROLES) {
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.valueOf(method), path));
            exchange.getAttributes().put(RouteAuthorizationFilter.ROLE_ATTRIBUTE, role);
            var forwarded = new AtomicBoolean();
            filter.filter(exchange, request -> { forwarded.set(true); return Mono.empty(); }).block();
            assertThat(forwarded.get()).as("%s %s %s", method, path, role).isEqualTo(allowed.contains(role));
        }
    }
}
