package com.mediflow.report.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.web.ReportRouteNotFoundResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration("reportWebConfiguration")
public class ReportWebConfiguration implements WebMvcConfigurer {
    private final ObjectMapper mapper;
    public ReportWebConfiguration(ObjectMapper mapper) { this.mapper = mapper; }
    @Override public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
        resolvers.add(0, new ReportRouteNotFoundResolver(mapper));
    }
}
