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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mediflow.lab.application.port.out.AuthenticatedStaffContextPort;
import com.mediflow.lab.application.port.out.CorrelationIdProvider;
import com.mediflow.lab.application.port.out.CurrentReaderPort;
import com.mediflow.lab.application.port.out.LabCatalogPort;
import com.mediflow.lab.application.port.out.LabReleaseAuthorityPort;
import com.mediflow.lab.application.port.out.LabReportRepositoryPort;
import com.mediflow.lab.application.port.out.PatientDemographicsPort;
import com.mediflow.lab.application.service.LabApplicationService;
import com.mediflow.lab.application.service.LabReportReleaseService;
import com.mediflow.lab.infrastructure.client.LabPatientDemographicsAdapter;
import com.mediflow.lab.infrastructure.client.LabPatientDemographicsFeignClient;
import com.mediflow.lab.infrastructure.client.LabReleaseAuthorityAdapter;
import com.mediflow.lab.infrastructure.client.LabReleaseAuthorityFeignClient;
import com.mediflow.lab.infrastructure.security.JwtProperties;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

@Configuration(proxyBeanMethods = false)
public class LabReviewConfiguration {

    @Bean
    LabReportReleaseService labReportReleaseService(LabReportRepositoryPort reports, LabCatalogPort catalog,
            AuthenticatedStaffContextPort staff, CurrentReaderPort readers,
            ObjectProvider<LabReleaseAuthorityPort> authority, LabApplicationService labs,
            CorrelationIdProvider correlationIds, PlatformTransactionManager transactions, Clock labClock,
            @Value("${mediflow.v2.lab-review:false}") boolean enabled,
            com.mediflow.lab.application.port.out.LabTestRepositoryPort tests,
            com.mediflow.lab.application.port.out.LabCriticalNoticeRepositoryPort notices,
            com.mediflow.lab.application.port.out.LabOutboxPort outbox,
            ObjectProvider<PatientDemographicsPort> demographics) {
        return new LabReportReleaseService(reports, catalog, staff, readers,
                authority.getIfAvailable(LabReleaseAuthorityPort::denyAll), labs, correlationIds,
                new TransactionTemplate(transactions), labClock, enabled, tests, notices, outbox,
                demographics.getIfAvailable(PatientDemographicsPort::unavailable));
    }

    /** The Organization and Patient clients exist only while the review workflow is on. */
    @Configuration(proxyBeanMethods = false)
    @EnableFeignClients(clients = {LabReleaseAuthorityFeignClient.class, LabPatientDemographicsFeignClient.class})
    @ConditionalOnProperty(name = "mediflow.v2.lab-review", havingValue = "true")
    static class ReleaseAuthorityConfiguration {
        @Bean
        CircuitBreaker labReleaseAuthorityCircuitBreaker() {
            return CircuitBreaker.of("organization-lab-release-authority", CircuitBreakerConfig.custom()
                    .slidingWindowSize(10)
                    .minimumNumberOfCalls(5)
                    .failureRateThreshold(50)
                    .waitDurationInOpenState(Duration.ofSeconds(10))
                    .build());
        }

        @Bean
        LabReleaseAuthorityPort labReleaseAuthorityPort(LabReleaseAuthorityFeignClient client, Clock labClock,
                JwtProperties jwt,
                @Qualifier("labReleaseAuthorityCircuitBreaker") CircuitBreaker circuitBreaker) {
            return new LabReleaseAuthorityAdapter(client, labClock, jwt.secret(), circuitBreaker);
        }

        @Bean
        CircuitBreaker labPatientDemographicsCircuitBreaker() {
            return CircuitBreaker.of("patient-lab-demographics", CircuitBreakerConfig.custom()
                    .slidingWindowSize(10)
                    .minimumNumberOfCalls(5)
                    .failureRateThreshold(50)
                    .waitDurationInOpenState(Duration.ofSeconds(10))
                    .build());
        }

        @Bean
        PatientDemographicsPort labPatientDemographicsPort(LabPatientDemographicsFeignClient client, Clock labClock,
                JwtProperties jwt,
                @Qualifier("labPatientDemographicsCircuitBreaker") CircuitBreaker circuitBreaker) {
            return new LabPatientDemographicsAdapter(client, labClock, jwt.secret(), circuitBreaker);
        }
    }
}
