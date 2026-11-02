package com.mediflow.report.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.port.out.CurrentReportAuthorityPort;
import com.mediflow.report.infrastructure.client.CoreReportAuthorityAdapter;
import com.mediflow.report.infrastructure.security.ReportScopeGuard;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Shared by standalone Report and its scoped Support-host security composition. */
@Configuration(proxyBeanMethods = false)
public class ReportAuthorityConfiguration {
    @Bean
    CurrentReportAuthorityPort reportCurrentAuthority(ObjectMapper mapper, ObjectProvider<DiscoveryClient> discovery,
            @Value("${mediflow.report.core-base-url:}") String baseUrl) {
        var breaker = CircuitBreaker.of("report-core-authority", CircuitBreakerConfig.custom().slidingWindowSize(10)
                .minimumNumberOfCalls(5).failureRateThreshold(50).waitDurationInOpenState(Duration.ofSeconds(10)).build());
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        return new CoreReportAuthorityAdapter(http, mapper, () -> {
            if (!baseUrl.isBlank()) return URI.create(baseUrl);
            var client = discovery.getIfAvailable();
            if (client == null) throw new IllegalStateException("Core discovery unavailable");
            var instances = client.getInstances("core-service");
            if (instances.isEmpty()) throw new IllegalStateException("Core discovery unavailable");
            return instances.get(0).getUri();
        }, breaker, Duration.ofSeconds(2));
    }

    @Bean("reportScopeGuard")
    ReportScopeGuard reportScopeGuard(CurrentReportAuthorityPort authority) {
        return new ReportScopeGuard(authority);
    }
}
