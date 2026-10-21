package com.mediflow.lab.infrastructure.config;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mediflow.lab.application.mapper.LabTestDtoMapper;
import com.mediflow.lab.application.port.out.AuthenticatedStaffContextPort;
import com.mediflow.lab.application.port.out.CorrelationIdProvider;
import com.mediflow.lab.application.port.out.LabClearanceRepositoryPort;
import com.mediflow.lab.application.port.out.LabEmergencyOverrideRepositoryPort;
import com.mediflow.lab.application.port.out.LabEventPublisherPort;
import com.mediflow.lab.application.port.out.LabExecutionAuthorityPort;
import com.mediflow.lab.application.port.out.LabClinicalOrderContextPort;
import com.mediflow.lab.application.port.out.LabOutboxPort;
import com.mediflow.lab.application.port.out.LabOutpatientMvpMode;
import com.mediflow.lab.application.port.out.LabTestRepositoryPort;
import com.mediflow.lab.application.port.in.CreateLabRequestUseCase;
import com.mediflow.lab.application.service.LabApplicationService;

/** Wires the Lab use cases and reads infrastructure-owned feature configuration. */
@Configuration
public class LabApplicationConfig {

    @Bean
    Clock labClock() {
        return Clock.systemUTC();
    }

    @Bean
    public LabApplicationService labApplicationService(
            LabTestRepositoryPort tests,
            LabEventPublisherPort publisher,
            LabTestDtoMapper mapper,
            CorrelationIdProvider correlationIds,
            LabOutboxPort outbox,
            LabClearanceRepositoryPort clearances,
            LabEmergencyOverrideRepositoryPort overrides,
            AuthenticatedStaffContextPort authenticatedStaff,
            ObjectProvider<LabClinicalOrderContextPort> clinicalOrders,
            CreateLabRequestUseCase requestCreation,
            Clock clock,
            @Value("${mediflow.features.care-finance-v2:false}") boolean careFinanceV2Enabled,
            LabOutpatientMvpMode outpatientMvpMode,
            ObjectProvider<LabExecutionAuthorityPort> executionAuthority) {
        return new LabApplicationService(tests, publisher, mapper, correlationIds, outbox,
                clearances, overrides, authenticatedStaff, careFinanceV2Enabled,
                outpatientMvpMode.enabled(), clock, clinicalOrders.getIfAvailable(), requestCreation,
                executionAuthority.getIfAvailable(LabExecutionAuthorityPort::unavailable));
    }
}
