package com.mediflow.gateway.filter;

import com.mediflow.common.security.Roles;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

/** Default-deny route/method/role matrix at the external Gateway boundary. */
@Component
public class RouteAuthorizationFilter implements GlobalFilter, Ordered {

    public static final String ROLE_ATTRIBUTE = RouteAuthorizationFilter.class.getName() + ".role";
    private static final PathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final List<RouteRule> RULES = List.of(
            rule("/api/v1/org/operating-rooms", HttpMethod.POST, Roles.ADMIN),
            rule("/api/v1/org/operating-rooms/{id}", HttpMethod.PUT, Roles.ADMIN),
            rule("/api/v1/org/departments", HttpMethod.GET, Roles.ADMIN, Roles.MANAGER, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/org/departments/**", HttpMethod.GET, Roles.ADMIN, Roles.MANAGER, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/org/departments", HttpMethod.POST, Roles.ADMIN),
            rule("/api/v1/org/departments/**", HttpMethod.PUT, Roles.ADMIN),
            rule("/api/v1/org/staff", HttpMethod.GET, Roles.ADMIN, Roles.MANAGER, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/org/staff/**", HttpMethod.GET, Roles.ADMIN, Roles.MANAGER, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/org/staff", HttpMethod.POST, Roles.ADMIN),
            rule("/api/v1/org/staff/**", HttpMethod.PUT, Roles.ADMIN),
            rule("/api/v1/org/accounts", HttpMethod.POST, Roles.ADMIN),
            rule("/api/v1/org/accounts/**", HttpMethod.PUT, Roles.ADMIN),

            rule("/api/v1/patients", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/patients/**", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/patients", HttpMethod.POST, Roles.ADMIN, Roles.NURSE),
            rule("/api/v1/patients/**", HttpMethod.PUT, Roles.ADMIN, Roles.NURSE),
            rule("/api/v1/patients/**", HttpMethod.DELETE, Roles.ADMIN),

            rule("/api/v1/appointments", HttpMethod.GET, Roles.ADMIN, Roles.MANAGER, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/appointments/**", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/appointments", HttpMethod.POST, Roles.ADMIN, Roles.NURSE),
            rule("/api/v1/appointments/*", HttpMethod.PUT, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/appointments/**/status", HttpMethod.PUT, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/appointments/**/check-in", HttpMethod.PUT, Roles.ADMIN, Roles.NURSE),
            rule("/api/v1/appointments/**/start-exam", HttpMethod.PUT, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/records/**", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/records", HttpMethod.POST, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/records/{id}/admission-referrals", HttpMethod.POST, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/records/**", HttpMethod.PUT, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/records/**/diagnoses", HttpMethod.POST, Roles.ADMIN, Roles.DOCTOR),

            // Inpatient routes mirror the downstream controller's method-level guards.
            rule("/api/v1/inpatient/admissions", HttpMethod.GET,
                    Roles.ADMIN, Roles.MANAGER, Roles.DOCTOR, Roles.NURSE, Roles.CASHIER),
            // Keep this to the controller's single-resource endpoint. A broad /** GET rule
            // would also authorize unsupported action paths such as treatment corrections.
            rule("/api/v1/inpatient/admissions/*", HttpMethod.GET,
                    Roles.ADMIN, Roles.DOCTOR, Roles.NURSE, Roles.CASHIER),
            rule("/api/v1/inpatient/beds", HttpMethod.GET,
                    Roles.ADMIN, Roles.MANAGER, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/inpatient/admissions", HttpMethod.POST,
                    Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/inpatient/admissions/**/admit", HttpMethod.POST,
                    Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/inpatient/admissions/**/treatments", HttpMethod.POST,
                    Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/inpatient/admissions/*/treatments/*/corrections", HttpMethod.POST,
                    Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/inpatient/admissions/**/order-references", HttpMethod.POST,
                    Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/inpatient/admissions/**/medical-discharge", HttpMethod.POST,
                    Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/inpatient/admissions/**/close", HttpMethod.POST,
                    Roles.ADMIN, Roles.CASHIER),
            rule("/api/v1/inpatient/admissions/**/cancel", HttpMethod.POST,
                    Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/inpatient/beds", HttpMethod.POST,
                    Roles.ADMIN, Roles.MANAGER),
            rule("/api/v1/inpatient/admissions/**/bed", HttpMethod.PUT,
                    Roles.ADMIN, Roles.NURSE),
            rule("/api/v1/inpatient/admissions/**/bed/transfer", HttpMethod.PUT,
                    Roles.ADMIN, Roles.NURSE),
            rule("/api/v1/inpatient/admissions/**/bed/release", HttpMethod.PUT,
                    Roles.ADMIN, Roles.NURSE),
            rule("/api/v1/inpatient/beds/**", HttpMethod.PUT,
                    Roles.ADMIN, Roles.MANAGER),

            // Only the currently implemented, feature-gated Surgery actions are exposed.
            rule("/api/v1/surgery/cases/*/preop", HttpMethod.POST,
                    Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/surgery/cases/*/cancel", HttpMethod.POST,
                    Roles.ADMIN, Roles.DOCTOR),

            rule("/api/v1/lab", HttpMethod.GET,
                    Roles.ADMIN, Roles.MANAGER, Roles.DOCTOR, Roles.NURSE, Roles.LAB_TECH),
            rule("/api/v1/lab/*", HttpMethod.GET,
                    Roles.ADMIN, Roles.DOCTOR, Roles.NURSE, Roles.LAB_TECH),
            rule("/api/v1/lab/patient/**", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/lab", HttpMethod.POST, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/lab/{id}/start", HttpMethod.PUT, Roles.ADMIN, Roles.LAB_TECH),
            rule("/api/v1/lab/{id}/cancel", HttpMethod.PUT, Roles.ADMIN, Roles.DOCTOR, Roles.LAB_TECH),
            rule("/api/v1/lab/{id}/results", HttpMethod.PUT, Roles.ADMIN, Roles.LAB_TECH),
            rule("/api/v1/lab/{id}/status", HttpMethod.PUT, Roles.ADMIN, Roles.LAB_TECH),

            rule("/api/v1/pharmacy/drugs", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.PHARMACIST),
            rule("/api/v1/pharmacy/drugs/**", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.PHARMACIST),
            rule("/api/v1/pharmacy/drugs", HttpMethod.POST, Roles.ADMIN, Roles.PHARMACIST),
            rule("/api/v1/pharmacy/drugs/**", HttpMethod.PUT, Roles.ADMIN, Roles.PHARMACIST),
            rule("/api/v1/pharmacy/prescriptions", HttpMethod.POST, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/pharmacy/prescriptions/**", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.PHARMACIST),
            rule("/api/v1/pharmacy/prescriptions/**", HttpMethod.PUT, Roles.ADMIN, Roles.PHARMACIST),
            rule("/api/v1/pharmacy/admin/outbox/**", HttpMethod.POST, Roles.ADMIN),

            rule("/api/v1/billing/invoices/**", HttpMethod.GET, Roles.ADMIN, Roles.CASHIER),
            rule("/api/v1/billing/patient/**", HttpMethod.GET, Roles.ADMIN, Roles.CASHIER),
            rule("/api/v1/billing/invoices", HttpMethod.POST, Roles.ADMIN, Roles.CASHIER),
            rule("/api/v1/billing/payment-requests/{id}/payments", HttpMethod.POST, Roles.ADMIN, Roles.CASHIER),
            rule("/api/v1/billing/invoices/**", HttpMethod.PUT, Roles.ADMIN, Roles.CASHIER),
            rule("/api/v1/billing/revenue", HttpMethod.GET, Roles.ADMIN, Roles.MANAGER),
            rule("/api/v1/billing/admin/outbox/**", HttpMethod.POST, Roles.ADMIN),

            rule("/api/v1/notifications/patient/**", HttpMethod.GET, Roles.ADMIN, Roles.NURSE, Roles.PATIENT),
            rule("/api/v1/notifications/**", HttpMethod.GET, Roles.ADMIN, Roles.PATIENT),
            rule("/api/v1/notifications/send", HttpMethod.POST, Roles.ADMIN, Roles.SYSTEM),

            rule("/api/v1/reports/operations/daily", HttpMethod.GET, Roles.ADMIN, Roles.MANAGER, Roles.DOCTOR),
            rule("/api/v1/reports/operations/surgery", HttpMethod.GET, Roles.ADMIN, Roles.MANAGER, Roles.DOCTOR),
            rule("/api/v1/reports/**", HttpMethod.GET, Roles.ADMIN, Roles.MANAGER)
    );

    private final GatewayErrorResponseWriter errors;

    public RouteAuthorizationFilter(GatewayErrorResponseWriter errors) {
        this.errors = errors;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (isPublic(path) || isInternalOnly(path)) {
            return chain.filter(exchange);
        }

        String role = exchange.getAttribute(ROLE_ATTRIBUTE);
        if (role == null) {
            return errors.write(exchange, HttpStatus.UNAUTHORIZED,
                    "AUTH_UNAUTHORIZED", "Authentication is required");
        }

        HttpMethod method = exchange.getRequest().getMethod();
        boolean allowed = method != null && RULES.stream()
                .filter(rule -> rule.method().equals(method) && rule.matches(path))
                .anyMatch(rule -> rule.roles().contains(role));
        if (!allowed) {
            return errors.write(exchange, HttpStatus.FORBIDDEN,
                    "AUTH_FORBIDDEN", "Role is not allowed to access this route");
        }
        return chain.filter(exchange);
    }

    private boolean isPublic(String path) {
        return "/api/v1/auth/login".equals(path)
                || "/api/v1/auth/refresh".equals(path)
                || "/actuator/health".equals(path)
                || "/actuator/info".equals(path);
    }

    private boolean isInternalOnly(String path) {
        return "/api/v1/org/accounts/verify".equals(path)
                || path.matches("/api/v1/org/staff/[^/]+/(exists|lookup)")
                || path.matches("/api/v1/org/departments/[^/]+/lookup")
                || path.matches("/api/v1/org/rooms/[^/]+/lookup")
                || path.matches("/api/v1/org/operating-rooms/[^/]+/lookup")
                || path.matches("/api/v1/org/staff/[^/]+/surgery-eligibility")
                || path.matches("/api/v1/patients/[^/]+/exists")
                || path.matches("/api/v1/inpatient/admissions/[^/]+/lookup");
    }

    private static RouteRule rule(String pattern, HttpMethod method, String... roles) {
        return new RouteRule(pattern, method, Set.of(roles));
    }

    private record RouteRule(String pattern, HttpMethod method, Set<String> roles) {
        private boolean matches(String path) {
            return PATH_MATCHER.match(pattern, path);
        }
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
