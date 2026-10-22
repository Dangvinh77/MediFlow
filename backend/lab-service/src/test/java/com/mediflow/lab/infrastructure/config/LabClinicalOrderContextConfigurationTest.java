package com.mediflow.lab.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.lab.application.port.out.LabClinicalOrderContextPort;
import com.mediflow.lab.infrastructure.client.LabClinicalOrderFeignClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.lab.infrastructure.security.JwtProperties;
import java.time.Clock;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;

class LabClinicalOrderContextConfigurationTest {
    @Test
    void clinicalContextStartsWithTheBillingCircuitBreakerAlsoPresent() {
        new ApplicationContextRunner()
                .withUserConfiguration(LabClinicalOrderContextConfiguration.class, FeignAutoConfiguration.class)
                .withPropertyValues("mediflow.features.care-finance-v2=true",
                        "mediflow.lab.clinical-context.enabled=true",
                        "mediflow.lab.clinical.base-url=http://127.0.0.1:8082")
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(JwtProperties.class, () -> new JwtProperties("review3-regression-secret-at-least-32-bytes"))
                .withBean("labBillingExecutionCircuitBreaker", CircuitBreaker.class,
                        () -> CircuitBreaker.ofDefaults("lab-billing-execution-authority"))
                .run(context -> assertThat(context).hasNotFailed()
                        .hasSingleBean(LabClinicalOrderContextPort.class));
    }

    @Test
    void authorityClientAndCircuitBreakerStayAbsentUnlessBothExplicitGatesAreEnabled() {
        new ApplicationContextRunner().withUserConfiguration(LabClinicalOrderContextConfiguration.class)
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(LabClinicalOrderContextPort.class)
                        .doesNotHaveBean(LabClinicalOrderFeignClient.class)
                        .doesNotHaveBean(CircuitBreaker.class));

        new ApplicationContextRunner().withUserConfiguration(LabClinicalOrderContextConfiguration.class)
                .withPropertyValues("mediflow.features.care-finance-v2=true",
                        "mediflow.lab.clinical-context.enabled=false")
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(LabClinicalOrderContextPort.class)
                        .doesNotHaveBean(LabClinicalOrderFeignClient.class));
    }
}
