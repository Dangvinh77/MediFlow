package com.mediflow.report.web;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.http.HttpHeaders;

class ReportRouteNotFoundResolverTest {
    private final ReportRouteNotFoundResolver resolver = new ReportRouteNotFoundResolver(new ObjectMapper().findAndRegisterModules());
    @Test void missingReportRouteKeepsRedactedEnvelopeAndCorrelation() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/reports/fake/private-value");
        request.addHeader("X-Correlation-Id", "report-not-found-test");
        var response = new MockHttpServletResponse();
        assertThat(resolver.resolveException(request, response, null,
                new NoHandlerFoundException("GET", request.getRequestURI(), new HttpHeaders()))).isNotNull();
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).contains("REPORT_NOT_FOUND", "report-not-found-test").doesNotContain("private-value");
    }
    @Test void foreignAndLookalikePathsAreNotClaimedAndOtherFailuresAreNotMasked() {
        for (String path : java.util.List.of("/api/v1/notifications/fake", "/api/v1/reports-not-mine/fake")) {
            var request = new MockHttpServletRequest("GET", path);
            var response = new MockHttpServletResponse();
            assertThat(resolver.resolveException(request, response, null,
                    new NoHandlerFoundException("GET", path, new HttpHeaders()))).isNull();
            assertThat(response.getStatus()).isEqualTo(200);
        }
        assertThat(resolver.resolveException(new MockHttpServletRequest("GET", "/api/v1/reports/fake"),
                new MockHttpServletResponse(), null, new IllegalStateException("database unavailable"))).isNull();
    }
}
