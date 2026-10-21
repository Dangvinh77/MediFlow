package com.mediflow.clinical.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.port.out.FinancialExecutionAuthorityPort;
import com.mediflow.clinical.infrastructure.client.ClinicalBillingExecutionAdapter;
import com.mediflow.clinical.infrastructure.client.ClinicalBillingExecutionFeignClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

/** Billing's account-locked execution admission is wired only with care-finance V2. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "mediflow.features.care-finance-v2", name = "enabled", havingValue = "true")
public class ClinicalBillingExecutionConfiguration {

    @Bean
    CircuitBreaker clinicalBillingExecutionCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .recordException(failure -> !(failure instanceof feign.FeignException http
                        && http.status() >= 400 && http.status() < 500 && http.status() != 429))
                .build();
        return CircuitBreaker.of("clinical-billing-execution", config);
    }

    @Bean
    FinancialExecutionAuthorityPort financialExecutionAuthorityPort(ClinicalBillingExecutionFeignClient client,
            ObjectMapper mapper, @Qualifier("clinicalCareFinanceClock") Clock clinicalCareFinanceClock,
            CircuitBreaker clinicalBillingExecutionCircuitBreaker) {
        return new ClinicalBillingExecutionAdapter(client, mapper, clinicalCareFinanceClock,
                clinicalBillingExecutionCircuitBreaker);
    }
}
