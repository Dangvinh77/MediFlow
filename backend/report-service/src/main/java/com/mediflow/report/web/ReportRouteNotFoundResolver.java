package com.mediflow.report.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Resource handlers have no controller package; keep Report's redacted 404 without claiming foreign paths. */
public final class ReportRouteNotFoundResolver implements HandlerExceptionResolver {
    private final ObjectMapper mapper;
    public ReportRouteNotFoundResolver(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception error) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!(path.equals("/api/v1/reports") || path.startsWith("/api/v1/reports/"))
                || !(error instanceof NoHandlerFoundException || error instanceof NoResourceFoundException)) return null;
        response.setStatus(404);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try {
            mapper.writeValue(response.getWriter(), ApiResponse.fail(ApiResponse.ApiError.of(
                    "REPORT_NOT_FOUND", "Report endpoint is unavailable"), request.getHeader(JwtClaims.HEADER_CORRELATION_ID)));
        } catch (IOException unavailable) { throw new UncheckedIOException(unavailable); }
        return new ModelAndView();
    }
}
