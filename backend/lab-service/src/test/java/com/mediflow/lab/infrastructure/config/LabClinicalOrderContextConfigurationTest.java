package com.mediflow.lab.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.lab.application.port.out.LabClinicalOrderContextPort;
import com.mediflow.lab.infrastructure.client.LabClinicalOrderFeignClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LabClinicalOrderContextConfigurationTest {
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
