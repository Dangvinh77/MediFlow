package com.mediflow.lab.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mediflow.lab.application.mapper.LabTestDtoMapper;
import com.mediflow.lab.application.port.out.AuthenticatedStaffIdPort;
import com.mediflow.lab.application.port.out.CorrelationIdProvider;
import com.mediflow.lab.application.port.out.LabClearanceRepositoryPort;
import com.mediflow.lab.application.port.out.LabEmergencyOverrideRepositoryPort;
import com.mediflow.lab.application.port.out.LabEventPublisherPort;
import com.mediflow.lab.application.port.out.LabOutboxPort;
import com.mediflow.lab.application.port.out.LabTestRepositoryPort;
import com.mediflow.lab.application.service.LabApplicationService;

/** Wires the Lab use cases and reads infrastructure-owned feature configuration. */
@Configuration
public class LabApplicationConfig {

    @Bean
    public LabApplicationService labApplicationService(
            LabTestRepositoryPort tests,
            LabEventPublisherPort publisher,
            LabTestDtoMapper mapper,
            CorrelationIdProvider correlationIds,
            LabOutboxPort outbox,
            LabClearanceRepositoryPort clearances,
            LabEmergencyOverrideRepositoryPort overrides,
            AuthenticatedStaffIdPort staffIds,
            @Value("${mediflow.features.care-finance-v2:false}") boolean careFinanceV2Enabled) {
        return new LabApplicationService(tests, publisher, mapper, correlationIds, outbox,
                clearances, overrides, staffIds, careFinanceV2Enabled);
    }
}
