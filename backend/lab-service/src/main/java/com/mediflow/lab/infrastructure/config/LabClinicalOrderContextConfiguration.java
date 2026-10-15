package com.mediflow.lab.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.lab.application.port.out.LabClinicalOrderContextPort;
import com.mediflow.lab.infrastructure.client.LabClinicalOrderContextAdapter;
import com.mediflow.lab.infrastructure.client.LabClinicalOrderFeignClient;
import com.mediflow.lab.infrastructure.security.JwtProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Explicit opt-in for the cross-context read; the default profile remains closed. */
@Configuration(proxyBeanMethods = false)
@EnableFeignClients(clients = LabClinicalOrderFeignClient.class)
@ConditionalOnProperty(name = {"mediflow.features.care-finance-v2", "mediflow.lab.clinical-context.enabled"},
        havingValue = "true")
public class LabClinicalOrderContextConfiguration {

    @Bean
    CircuitBreaker labClinicalOrderContextCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .recordException(failure -> !(failure instanceof feign.FeignException http
                        && http.status() >= 400 && http.status() < 500 && http.status() != 429))
                .build();
        return CircuitBreaker.of("clinical-lab-order-context", config);
    }

    @Bean
    LabClinicalOrderContextPort labClinicalOrderContext(LabClinicalOrderFeignClient client,
            Clock labClock, JwtProperties jwt, ObjectMapper mapper, CircuitBreaker circuitBreaker) {
        return new LabClinicalOrderContextAdapter(client, labClock, jwt, mapper, circuitBreaker);
    }
}
