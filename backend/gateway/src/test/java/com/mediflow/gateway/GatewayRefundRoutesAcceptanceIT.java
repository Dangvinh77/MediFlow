package com.mediflow.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.gateway.filter.GatewayErrorResponseWriter;
import com.mediflow.gateway.filter.RouteAuthorizationFilter;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.assertThat;

/** Explicit owner acceptance: run with -Dtest=GatewayRefundRoutesAcceptanceIT. */
class GatewayRefundRoutesAcceptanceIT {
    private final RouteAuthorizationFilter filter = new RouteAuthorizationFilter(
            new GatewayErrorResponseWriter(new ObjectMapper().findAndRegisterModules()));

    @ParameterizedTest(name = "{0} {1} role={2} forwarded={3}")
    @CsvSource({
        "GET,/api/v1/billing/prescription-adjustments/00000000-0000-0000-0000-000000000001/refunds-due,ADMIN,true",
        "GET,/api/v1/billing/prescription-adjustments/00000000-0000-0000-0000-000000000001/refunds-due,CASHIER,true",
        "GET,/api/v1/billing/lab-adjustments/00000000-0000-0000-0000-000000000001/refunds-due,ADMIN,true",
        "GET,/api/v1/billing/lab-adjustments/00000000-0000-0000-0000-000000000001/refunds-due,CASHIER,true",
        "GET,/api/v1/billing/exam-adjustments/00000000-0000-0000-0000-000000000001/refunds-due,ADMIN,true",
        "GET,/api/v1/billing/exam-adjustments/00000000-0000-0000-0000-000000000001/refunds-due,CASHIER,true",
        "GET,/api/v1/billing/exam-adjustments/00000000-0000-0000-0000-000000000001/refunds-due,DOCTOR,false",
        "POST,/api/v1/billing/exam-adjustments/00000000-0000-0000-0000-000000000001/refunds-due,ADMIN,false",
        "GET,/api/v1/billing/exam-adjustments/00000000-0000-0000-0000-000000000001/refunds-due/extra,ADMIN,false",
        "POST,/api/v1/billing/financial-clearances/00000000-0000-0000-0000-000000000001/execution-starts,SYSTEM,true",
        "POST,/api/v1/billing/transactions/00000000-0000-0000-0000-000000000001/refunds,ADMIN,true",
        "POST,/api/v1/billing/transactions/00000000-0000-0000-0000-000000000001/refunds,CASHIER,true",
        "GET,/api/v1/billing/prescription-adjustments/00000000-0000-0000-0000-000000000001/refunds-due,DOCTOR,false",
        "GET,/api/v1/billing/lab-adjustments/00000000-0000-0000-0000-000000000001/refunds-due,DOCTOR,false",
        "POST,/api/v1/billing/financial-clearances/00000000-0000-0000-0000-000000000001/execution-starts,ADMIN,false",
        "POST,/api/v1/billing/transactions/00000000-0000-0000-0000-000000000001/refunds,PATIENT,false",
        "POST,/api/v1/billing/transactions/00000000-0000-0000-0000-000000000001/refunds,SYSTEM,false",
        "POST,/api/v1/billing/prescription-adjustments/00000000-0000-0000-0000-000000000001/refunds-due,ADMIN,false",
        "GET,/api/v1/billing/transactions/00000000-0000-0000-0000-000000000001/refunds,ADMIN,false",
        "GET,/api/v1/billing/prescription-adjustments/00000000-0000-0000-0000-000000000001/refunds-due/extra,ADMIN,false",
        "GET,/api/v1/billing/lab-adjustments/00000000-0000-0000-0000-000000000001/refunds-due/extra,ADMIN,false",
        "POST,/api/v1/billing/transactions/00000000-0000-0000-0000-000000000001/refunds/extra,ADMIN,false"
    })
    void outpatientRefundRoutesMatchLiveControllerPolicy(String method, String path, String role, boolean allowed) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.valueOf(method), path));
        exchange.getAttributes().put(RouteAuthorizationFilter.ROLE_ATTRIBUTE, role);
        var forwarded = new AtomicBoolean();
        filter.filter(exchange, request -> { forwarded.set(true); return Mono.empty(); }).block();
        assertThat(forwarded.get()).as("%s %s role=%s", method, path, role).isEqualTo(allowed);
        if (!allowed) assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
