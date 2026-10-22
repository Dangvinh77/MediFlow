package com.mediflow.lab.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.mediflow.lab.application.mapper.LabTestDtoMapper;
import com.mediflow.lab.application.port.in.CreateLabRequestUseCase;
import com.mediflow.lab.application.port.out.AuthenticatedStaffContextPort;
import com.mediflow.lab.application.port.out.CorrelationIdProvider;
import com.mediflow.lab.application.port.out.LabClearanceRepositoryPort;
import com.mediflow.lab.application.port.out.LabEmergencyOverrideRepositoryPort;
import com.mediflow.lab.application.port.out.LabEventPublisherPort;
import com.mediflow.lab.application.port.out.LabExecutionAuthorityPort;
import com.mediflow.lab.application.port.out.LabOutboxPort;
import com.mediflow.lab.application.port.out.LabOutpatientMvpMode;
import com.mediflow.lab.application.port.out.LabTestRepositoryPort;
import com.mediflow.lab.application.service.LabApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class LabApplicationConfigTest {

    @Test
    void compatibilityBootDoesNotRequireBillingAuthorityBean() {
        new ApplicationContextRunner()
                .withUserConfiguration(LabApplicationConfig.class, CompatibilityDependencies.class)
                .withPropertyValues("mediflow.features.care-finance-v2=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(LabApplicationService.class);
                    assertThat(context).doesNotHaveBean(LabExecutionAuthorityPort.class);
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CompatibilityDependencies {
        @Bean LabTestRepositoryPort tests() { return mock(LabTestRepositoryPort.class); }
        @Bean LabEventPublisherPort publisher() { return mock(LabEventPublisherPort.class); }
        @Bean LabTestDtoMapper mapper() { return mock(LabTestDtoMapper.class); }
        @Bean CorrelationIdProvider correlations() { return mock(CorrelationIdProvider.class); }
        @Bean LabOutboxPort outbox() { return mock(LabOutboxPort.class); }
        @Bean LabClearanceRepositoryPort clearances() { return mock(LabClearanceRepositoryPort.class); }
        @Bean LabEmergencyOverrideRepositoryPort overrides() { return mock(LabEmergencyOverrideRepositoryPort.class); }
        @Bean AuthenticatedStaffContextPort staff() { return mock(AuthenticatedStaffContextPort.class); }
        @Bean CreateLabRequestUseCase creation() { return mock(CreateLabRequestUseCase.class); }
        @Bean LabOutpatientMvpMode outpatientMode() { return new LabOutpatientMvpMode(false); }
    }
}
