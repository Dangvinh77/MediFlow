package com.mediflow.lab.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.lab.application.port.out.LabExecutionAuthorityPort;
import com.mediflow.lab.infrastructure.client.LabBillingClearanceFeignClient;
import com.mediflow.lab.infrastructure.client.LabBillingExecutionAuthorityAdapter;
import com.mediflow.lab.infrastructure.security.JwtProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Billing current-authority integration is available only with care-finance V2 enabled. */
@Configuration(proxyBeanMethods = false)
@EnableFeignClients(clients = LabBillingClearanceFeignClient.class)
@ConditionalOnProperty(name = "mediflow.features.care-finance-v2", havingValue = "true")
public class LabBillingExecutionAuthorityConfiguration {

    @Bean
    CircuitBreaker labBillingExecutionCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .recordException(failure -> !(failure instanceof feign.FeignException http
                        && http.status() >= 400 && http.status() < 500 && http.status() != 429))
                .build();
        return CircuitBreaker.of("lab-billing-execution-authority", config);
    }

    @Bean
    LabExecutionAuthorityPort labExecutionAuthorityPort(LabBillingClearanceFeignClient client,
            ObjectMapper mapper, Clock labClock, JwtProperties jwt, CircuitBreaker labBillingExecutionCircuitBreaker) {
        return new LabBillingExecutionAuthorityAdapter(client, mapper, labClock, jwt,
                labBillingExecutionCircuitBreaker);
    }
}
