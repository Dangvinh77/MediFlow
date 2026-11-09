package com.mediflow.pharmacy.infrastructure.config;

import com.mediflow.pharmacy.application.port.in.GetPrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.SearchPrescriptionsUseCase;
import com.mediflow.pharmacy.application.port.out.ClinicalReadDecisionPort;
import com.mediflow.pharmacy.application.service.PrescriptionReadAccessService;
import com.mediflow.pharmacy.infrastructure.client.PharmacyClinicalReadDecisionAdapter;
import com.mediflow.pharmacy.infrastructure.client.PharmacyClinicalReadDecisionFeignClient;
import com.mediflow.pharmacy.infrastructure.security.JwtProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PrescriptionReadAccessConfiguration {

    @Bean
    PrescriptionReadAccessService prescriptionReadAccessService(GetPrescriptionUseCase prescriptions,
            SearchPrescriptionsUseCase searches, ObjectProvider<ClinicalReadDecisionPort> decisions,
            @Value("${mediflow.v2.record-access-policy:false}") boolean enabled) {
        return new PrescriptionReadAccessService(prescriptions, searches, decisions.getIfAvailable(), enabled);
    }

    /** The Clinical client exists only when the policy is on, so the default profile keeps no new dependency. */
    @Configuration(proxyBeanMethods = false)
    @EnableFeignClients(clients = PharmacyClinicalReadDecisionFeignClient.class)
    @ConditionalOnProperty(name = "mediflow.v2.record-access-policy", havingValue = "true")
    static class ClinicalReadDecisionConfiguration {

        @Bean
        CircuitBreaker pharmacyClinicalReadDecisionCircuitBreaker() {
            return CircuitBreaker.of("clinical-read-decision", CircuitBreakerConfig.custom()
                    .slidingWindowSize(10)
                    .minimumNumberOfCalls(5)
                    .failureRateThreshold(50)
                    .waitDurationInOpenState(Duration.ofSeconds(10))
                    .build());
        }

        @Bean
        ClinicalReadDecisionPort clinicalReadDecisionPort(PharmacyClinicalReadDecisionFeignClient client,
                ObjectProvider<Clock> clock, JwtProperties jwt,
                @Qualifier("pharmacyClinicalReadDecisionCircuitBreaker") CircuitBreaker circuitBreaker) {
            return new PharmacyClinicalReadDecisionAdapter(client, clock.getIfAvailable(Clock::systemUTC), jwt.secret(),
                    circuitBreaker);
        }
    }
}
