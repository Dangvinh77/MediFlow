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
            ObjectProvider<LabExecutionAuthorityPort> executionAuthority,
            ObjectProvider<com.mediflow.lab.application.port.out.LabReportRepositoryPort> reports,
            ObjectProvider<com.mediflow.lab.application.port.out.LabCatalogPort> catalog,
            @Value("${mediflow.v2.lab-review:false}") boolean labReviewEnabled) {
        return new LabApplicationService(tests, publisher, mapper, correlationIds, outbox,
                clearances, overrides, authenticatedStaff, careFinanceV2Enabled,
                outpatientMvpMode.enabled(), clock, clinicalOrders.getIfAvailable(), requestCreation,
                executionAuthority.getIfAvailable(LabExecutionAuthorityPort::unavailable),
                reviewGate(labReviewEnabled, reports.getIfAvailable(), catalog.getIfAvailable()));
    }

    /**
     * With the review workflow on, a test that entered it, or that is bound to a catalogue template and so
     * can enter it, may only complete through a second-person release. Legacy tests keep the v1 entry.
     */
    static com.mediflow.lab.application.port.out.LabResultWorkflowGate reviewGate(boolean enabled,
            com.mediflow.lab.application.port.out.LabReportRepositoryPort reports,
            com.mediflow.lab.application.port.out.LabCatalogPort catalog) {
        if (!enabled || reports == null) {
            return com.mediflow.lab.application.port.out.LabResultWorkflowGate.never();
        }
        return testId -> reports.workflowVersion(testId) == 1
                || (catalog != null && catalog.binding(testId).isPresent());
    }
}
