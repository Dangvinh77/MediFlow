package com.mediflow.surgery.infrastructure.config;

import com.mediflow.surgery.application.port.in.BeginPreopUseCase;
import com.mediflow.surgery.application.port.in.ManageSurgeryConsentUseCase;
import com.mediflow.surgery.application.port.in.UpdateChecklistItemUseCase;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryChecklistRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryCommandReceiptPort;
import com.mediflow.surgery.application.port.out.SurgeryConsentRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryResourceReservationPort;
import com.mediflow.surgery.application.port.out.SurgeryScheduleRepositoryPort;
import com.mediflow.surgery.application.service.SurgeryChecklistApplicationService;
import com.mediflow.surgery.application.service.SurgeryConsentApplicationService;
import com.mediflow.surgery.application.service.SurgeryPreopApplicationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("!test")
@ConditionalOnProperty(prefix = "mediflow.features.surgery", name = "enabled", havingValue = "true")
public class SurgeryApplicationServiceConfiguration {

    @Bean
    BeginPreopUseCase beginPreopUseCase(SurgeryCaseRepositoryPort cases,
                                        SurgeryCommandReceiptPort receipts,
                                        SurgeryClockPort clock) {
        return new SurgeryPreopApplicationService(cases, receipts, clock);
    }

    @Bean
    UpdateChecklistItemUseCase updateChecklistItemUseCase(
            SurgeryCaseRepositoryPort cases,
            SurgeryChecklistRepositoryPort checklists,
            SurgeryCommandReceiptPort receipts,
            SurgeryScheduleRepositoryPort schedules,
            SurgeryResourceReservationPort reservations,
            SurgeryClockPort clock) {
        return new SurgeryChecklistApplicationService(
                cases, checklists, receipts, schedules, reservations, clock);
    }

    @Bean
    ManageSurgeryConsentUseCase manageSurgeryConsentUseCase(
            SurgeryCaseRepositoryPort cases,
            SurgeryConsentRepositoryPort consents,
            SurgeryScheduleRepositoryPort schedules,
            SurgeryResourceReservationPort reservations,
            SurgeryCommandReceiptPort receipts,
            SurgeryClockPort clock) {
        return new SurgeryConsentApplicationService(
                cases, consents, schedules, reservations, receipts, clock);
    }
}
