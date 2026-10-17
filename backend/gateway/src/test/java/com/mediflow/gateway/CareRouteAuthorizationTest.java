package com.mediflow.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.gateway.filter.GatewayErrorResponseWriter;
import com.mediflow.gateway.filter.RouteAuthorizationFilter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.assertThat;

/** Edge policy only; actual JWT/service verification is exercised by the web suites. */
class CareRouteAuthorizationTest {
    private static final String ID = "00000000-0000-0000-0000-000000000001";
    private static final List<String> ROLES = List.of("ADMIN", "MANAGER", "DOCTOR", "NURSE",
            "LAB_TECH", "PHARMACIST", "CASHIER", "PATIENT", "SYSTEM");
    private final RouteAuthorizationFilter filter = new RouteAuthorizationFilter(
            new GatewayErrorResponseWriter(new ObjectMapper().findAndRegisterModules()));

    private record MatrixRow(HttpMethod method, String path, Set<String> roles) { }

    static Stream<Arguments> matrix() {
        List<MatrixRow> rows = List.of(
                new MatrixRow(HttpMethod.GET, "/api/v1/pharmacy/drugs", Set.of("ADMIN", "DOCTOR", "PHARMACIST")),
                new MatrixRow(HttpMethod.GET, "/api/v1/pharmacy/drugs/" + ID, Set.of("ADMIN", "DOCTOR", "PHARMACIST")),
                new MatrixRow(HttpMethod.POST, "/api/v1/pharmacy/drugs", Set.of("ADMIN", "PHARMACIST")),
                new MatrixRow(HttpMethod.PUT, "/api/v1/pharmacy/drugs/" + ID + "/stock", Set.of("ADMIN", "PHARMACIST")),
                new MatrixRow(HttpMethod.POST, "/api/v1/pharmacy/prescriptions", Set.of("ADMIN", "DOCTOR")),
                new MatrixRow(HttpMethod.GET, "/api/v1/pharmacy/prescriptions/" + ID, Set.of("ADMIN", "DOCTOR", "PHARMACIST")),
                new MatrixRow(HttpMethod.PUT, "/api/v1/pharmacy/prescriptions/" + ID + "/cancel", Set.of("ADMIN", "DOCTOR")),
                new MatrixRow(HttpMethod.PUT, "/api/v1/pharmacy/prescriptions/" + ID + "/dispense", Set.of("ADMIN", "PHARMACIST")),
                new MatrixRow(HttpMethod.POST, "/api/v1/pharmacy/admin/outbox/" + ID + "/replay", Set.of("ADMIN")),
                new MatrixRow(HttpMethod.GET, "/api/v1/surgery/cases", Set.of("ADMIN","MANAGER","DOCTOR","NURSE")),
                new MatrixRow(HttpMethod.GET, "/api/v1/surgery/cases/" + ID, Set.of("ADMIN","MANAGER","DOCTOR","NURSE")),
                new MatrixRow(HttpMethod.PUT, "/api/v1/surgery/cases/" + ID + "/schedule", Set.of("ADMIN","MANAGER","DOCTOR")),
                new MatrixRow(HttpMethod.GET, "/api/v1/lab", Set.of("ADMIN", "MANAGER", "DOCTOR", "NURSE", "LAB_TECH")),
                new MatrixRow(HttpMethod.GET, "/api/v1/lab/" + ID, Set.of("ADMIN", "DOCTOR", "NURSE", "LAB_TECH")),
                new MatrixRow(HttpMethod.PUT, "/api/v1/lab/" + ID + "/start", Set.of("ADMIN", "LAB_TECH")),
                new MatrixRow(HttpMethod.PUT, "/api/v1/lab/" + ID + "/cancel", Set.of("ADMIN", "DOCTOR", "LAB_TECH")),
                new MatrixRow(HttpMethod.PUT, "/api/v1/lab/" + ID + "/results", Set.of("ADMIN", "LAB_TECH")),
                new MatrixRow(HttpMethod.PUT, "/api/v1/lab/" + ID + "/status", Set.of("ADMIN", "LAB_TECH")),
                new MatrixRow(HttpMethod.POST, "/api/v1/records/" + ID + "/lab-orders", Set.of("ADMIN", "DOCTOR")),
                new MatrixRow(HttpMethod.GET, "/api/v1/records/" + ID, Set.of("ADMIN", "DOCTOR", "NURSE")),
                new MatrixRow(HttpMethod.GET, "/api/v1/records/patient/" + ID, Set.of("ADMIN", "DOCTOR", "NURSE")),
                new MatrixRow(HttpMethod.PUT, "/api/v1/records/" + ID, Set.of("ADMIN", "DOCTOR")),
                new MatrixRow(HttpMethod.PUT, "/api/v1/records/" + ID + "/complete", Set.of("ADMIN", "DOCTOR")),
                new MatrixRow(HttpMethod.POST, "/api/v1/records/" + ID + "/diagnoses", Set.of("ADMIN", "DOCTOR")),
                new MatrixRow(HttpMethod.POST, "/api/v1/records/" + ID + "/admission-referrals", Set.of("ADMIN", "DOCTOR")),
                new MatrixRow(HttpMethod.PUT, "/api/v1/appointments/" + ID + "/check-in", Set.of("ADMIN", "NURSE")),
                new MatrixRow(HttpMethod.PUT, "/api/v1/appointments/" + ID + "/start-exam", Set.of("ADMIN", "DOCTOR")),
                new MatrixRow(HttpMethod.PUT, "/api/v1/appointments/" + ID, Set.of("ADMIN", "DOCTOR", "NURSE")),
                new MatrixRow(HttpMethod.PUT, "/api/v1/appointments/" + ID + "/status", Set.of("ADMIN", "DOCTOR", "NURSE")));
        return rows.stream().flatMap(row -> ROLES.stream().map(role ->
                Arguments.of(row.method(), row.path(), role, row.roles().contains(role))));
    }

    @ParameterizedTest(name = "{0} {1}: {2} allowed={3}")
    @MethodSource("matrix")
    void everyCanonicalRoleMatchesDownstreamPolicy(HttpMethod method, String path, String role, boolean allowed) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.method(method, path));
        exchange.getAttributes().put(RouteAuthorizationFilter.ROLE_ATTRIBUTE, role);
        var forwarded = new AtomicBoolean();
        filter.filter(exchange, request -> { forwarded.set(true); return Mono.empty(); }).block();
        assertThat(forwarded.get()).isEqualTo(allowed);
        if (!allowed) assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test void unknownPharmacyPathsAndMethodsCannotReachAnUpstream() {
        for (String role : ROLES) {
            for (String path : List.of("/api/v1/pharmacy/drugs/" + ID + "/fake/nested",
                    "/api/v1/pharmacy/prescriptions/" + ID + "/dispense/extra",
                    "/api/v1/pharmacy/prescriptions/" + ID + "/nested/cancel",
                    "/api/v1/pharmacy/admin/outbox/" + ID + "/nested/replay")) {
                everyCanonicalRoleMatchesDownstreamPolicy(HttpMethod.GET, path, role, false);
                everyCanonicalRoleMatchesDownstreamPolicy(HttpMethod.PUT, path, role, false);
                everyCanonicalRoleMatchesDownstreamPolicy(HttpMethod.POST, path, role, false);
            }
            everyCanonicalRoleMatchesDownstreamPolicy(HttpMethod.PUT, "/api/v1/pharmacy/drugs/" + ID, role, false);
            everyCanonicalRoleMatchesDownstreamPolicy(HttpMethod.GET, "/api/v1/pharmacy/prescriptions/" + ID + "/dispense", role, false);
        }
    }

    @Test void unknownNestedCommandsAndWrongMethodsAreDenied() {
        for (String path : List.of("/api/v1/lab/" + ID + "/nested/start",
                "/api/v1/appointments/" + ID + "/check-in/extra",
                "/api/v1/appointments/" + ID + "/nested/start-exam",
                "/api/v1/records/" + ID + "/lab-orders/extra",
                "/api/v1/records/" + ID + "/admission-referrals/extra")) {
            everyCanonicalRoleMatchesDownstreamPolicy(path.contains("/records/") ? HttpMethod.POST : HttpMethod.PUT,
                    path, "ADMIN", false);
        }
        everyCanonicalRoleMatchesDownstreamPolicy(HttpMethod.POST, "/api/v1/lab/" + ID + "/start", "ADMIN", false);
        everyCanonicalRoleMatchesDownstreamPolicy(HttpMethod.DELETE, "/api/v1/records/" + ID + "/lab-orders", "DOCTOR", false);
        everyCanonicalRoleMatchesDownstreamPolicy(HttpMethod.GET, "/api/v1/records/" + ID + "/lab-orders", "DOCTOR", false);
        everyCanonicalRoleMatchesDownstreamPolicy(HttpMethod.PUT, "/api/v1/records/" + ID + "/lab-orders", "DOCTOR", false);
        everyCanonicalRoleMatchesDownstreamPolicy(HttpMethod.POST, "/api/v1/records/" + ID + "/lab-orders/extra", "DOCTOR", false);
        everyCanonicalRoleMatchesDownstreamPolicy(HttpMethod.GET, "/api/v1/records/" + ID + "/lab-orders/extra", "DOCTOR", false);
        everyCanonicalRoleMatchesDownstreamPolicy(HttpMethod.PUT, "/api/v1/records/" + ID + "/lab-orders/extra", "DOCTOR", false);
        everyCanonicalRoleMatchesDownstreamPolicy(HttpMethod.GET, "/api/v1/records/" + ID + "/prescription-context", "DOCTOR", false);
        everyCanonicalRoleMatchesDownstreamPolicy(HttpMethod.POST, "/api/v1/appointments/" + ID + "/check-in", "ADMIN", false);
        everyCanonicalRoleMatchesDownstreamPolicy(HttpMethod.POST, "/api/v1/surgery/cases/" + ID + "/schedule", "ADMIN", false);
        everyCanonicalRoleMatchesDownstreamPolicy(HttpMethod.GET, "/api/v1/surgery/cases/" + ID + "/evidence", "ADMIN", false);
    }
}
