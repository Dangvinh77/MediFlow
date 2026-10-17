package com.mediflow.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.gateway.filter.GatewayErrorResponseWriter;
import com.mediflow.gateway.filter.RouteAuthorizationFilter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.assertThat;

/** Frozen downstream controller guards; unpublished endpoints stay denied, including deferred work. */
class RouteRoleContractTest {
    private static final List<String> ROLES = List.of("ADMIN", "MANAGER", "DOCTOR", "NURSE",
            "LAB_TECH", "PHARMACIST", "CASHIER", "PATIENT", "SYSTEM");
    private final RouteAuthorizationFilter filter = new RouteAuthorizationFilter(
            new GatewayErrorResponseWriter(new ObjectMapper().findAndRegisterModules()));

    static Stream<Arguments> contracts() throws Exception {
        try (var reader = new BufferedReader(new InputStreamReader(
                RouteRoleContractTest.class.getResourceAsStream("/route-role-contracts.csv"),
                StandardCharsets.UTF_8))) {
            // Materialize before closing the reader. Roles come from the downstream guard,
            // publication is an explicit snapshot, not inferred from the filter under test.
            return reader.lines().filter(line -> !line.startsWith("#") && !line.isBlank())
                    .flatMap(line -> {
                        String[] fields = line.split(",");
                        Set<String> roles = Set.copyOf(Arrays.asList(fields[3].split("\\|")));
                        String path = fields[2].replaceAll("\\{[^}]+}",
                                "00000000-0000-0000-0000-000000000001");
                        boolean published = Boolean.parseBoolean(fields[4]);
                        return ROLES.stream().map(role -> Arguments.of(fields[0],
                                HttpMethod.valueOf(fields[1]), path, role,
                                published && roles.contains(role)));
                    }).toList().stream();
        }
    }

    @ParameterizedTest(name = "{0}: {1} {2} role={3} allowed={4}")
    @MethodSource("contracts")
    void matchesDownstreamGuardOrExplicitlyDeniesUnpublishedEndpoint(
            String service, HttpMethod method, String path, String role, boolean allowed) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.method(method, path));
        exchange.getAttributes().put(RouteAuthorizationFilter.ROLE_ATTRIBUTE, role);
        var forwarded = new AtomicBoolean();
        filter.filter(exchange, request -> { forwarded.set(true); return Mono.empty(); }).block();
        assertThat(forwarded.get()).as("%s %s %s role=%s", service, method, path, role)
                .isEqualTo(allowed);
        if (!allowed) {
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
