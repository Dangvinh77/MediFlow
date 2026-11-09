package com.mediflow.lab.infrastructure.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mediflow.lab.application.port.in.ManageLabTestUseCase;
import com.mediflow.lab.application.port.out.ClinicalReadDecisionPort;
import com.mediflow.lab.application.port.out.CorrelationIdProvider;
import com.mediflow.lab.application.port.out.CurrentReaderPort;
import com.mediflow.lab.application.service.LabReadAccessService;
import com.mediflow.lab.infrastructure.client.LabClinicalReadDecisionAdapter;
import com.mediflow.lab.infrastructure.client.LabClinicalReadDecisionFeignClient;
import com.mediflow.lab.infrastructure.security.JwtProperties;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

@Configuration(proxyBeanMethods = false)
public class LabReadAccessConfiguration {

    @Bean
    LabReadAccessService labReadAccessService(ManageLabTestUseCase labs,
            ObjectProvider<ClinicalReadDecisionPort> decisions, CurrentReaderPort readers,
            CorrelationIdProvider correlationIds,
            @Value("${mediflow.v2.record-access-policy:false}") boolean enabled) {
        return new LabReadAccessService(labs, decisions.getIfAvailable(), readers, correlationIds, enabled);
    }

    /** The Clinical client exists only when the policy is on, so the default profile keeps no new dependency. */
    @Configuration(proxyBeanMethods = false)
    @EnableFeignClients(clients = LabClinicalReadDecisionFeignClient.class)
    @ConditionalOnProperty(name = "mediflow.v2.record-access-policy", havingValue = "true")
    static class ClinicalReadDecisionConfiguration {

        @Bean
        CircuitBreaker labClinicalReadDecisionCircuitBreaker() {
            return CircuitBreaker.of("clinical-read-decision", CircuitBreakerConfig.custom()
                    .slidingWindowSize(10)
                    .minimumNumberOfCalls(5)
                    .failureRateThreshold(50)
                    .waitDurationInOpenState(Duration.ofSeconds(10))
                    .build());
        }

        @Bean
        ClinicalReadDecisionPort clinicalReadDecisionPort(LabClinicalReadDecisionFeignClient client, Clock labClock,
                JwtProperties jwt,
                @Qualifier("labClinicalReadDecisionCircuitBreaker") CircuitBreaker circuitBreaker) {
            return new LabClinicalReadDecisionAdapter(client, labClock, jwt.secret(), circuitBreaker);
        }
    }
}
