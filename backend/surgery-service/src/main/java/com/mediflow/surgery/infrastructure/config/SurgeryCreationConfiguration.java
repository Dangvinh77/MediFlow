package com.mediflow.surgery.infrastructure.config;

import com.mediflow.surgery.application.port.in.CreateSurgeryCaseUseCase;
import com.mediflow.surgery.application.port.out.SurgeryCareEventCapturePort;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryChecklistRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryCreationAuthorityPort;
import com.mediflow.surgery.application.port.out.SurgeryCreationReceiptPort;
import com.mediflow.surgery.application.port.out.SurgeryUnitOfWorkPort;
import com.mediflow.surgery.application.service.SurgeryCreationApplicationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Enabling intake without a real authority provider fails startup, never silently enables defaults. */
@Configuration(proxyBeanMethods = false)
@Profile("!test")
@ConditionalOnProperty(name = {"mediflow.features.surgery.enabled", "mediflow.surgery.creation.api.enabled"},
        havingValue = "true")
public class SurgeryCreationConfiguration {
    @Bean
    CreateSurgeryCaseUseCase createSurgeryCaseUseCase(SurgeryCaseRepositoryPort cases,
            SurgeryChecklistRepositoryPort checklists, SurgeryCreationReceiptPort receipts,
            SurgeryCreationAuthorityPort authority, SurgeryCareEventCapturePort events,
            SurgeryClockPort clock, SurgeryUnitOfWorkPort unitOfWork) {
        return new SurgeryCreationApplicationService(cases, checklists, receipts, authority, events, clock, unitOfWork);
    }
}
