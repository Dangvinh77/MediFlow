package com.mediflow.surgery.infrastructure.config;

import com.mediflow.surgery.application.port.in.ApplySurgeryAuthorityInvalidationUseCase;
import com.mediflow.surgery.application.port.in.QuerySurgeryAuthorityInvalidationsUseCase;
import com.mediflow.surgery.application.port.in.ReceiveSurgeryAuthorityChangeUseCase;
import com.mediflow.surgery.application.port.in.RecordSurgeryAuthorityInvalidationRetryUseCase;
import com.mediflow.surgery.application.port.out.SurgeryAuthorityInvalidationPort;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryInboxPort;
import com.mediflow.surgery.application.port.out.SurgeryResourceReservationPort;
import com.mediflow.surgery.application.port.out.SurgeryScheduleRepositoryPort;
import com.mediflow.surgery.application.service.SurgeryAuthorityChangeService;
import com.mediflow.surgery.application.service.SurgeryAuthorityInvalidationQueryService;
import com.mediflow.surgery.application.service.SurgeryAuthorityInvalidationRetryService;
import com.mediflow.surgery.application.service.SurgeryAuthorityInvalidationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("!test")
@ConditionalOnProperty(prefix = "mediflow.features.surgery", name = "enabled", havingValue = "true")
public class SurgeryAuthorityApplicationConfiguration {
    @Bean ReceiveSurgeryAuthorityChangeUseCase receiveSurgeryAuthorityChange(SurgeryInboxPort inbox, SurgeryAuthorityInvalidationPort jobs, SurgeryClockPort clock) {
        return new SurgeryAuthorityChangeService(inbox, jobs, clock);
    }
    @Bean QuerySurgeryAuthorityInvalidationsUseCase querySurgeryAuthorityInvalidations(SurgeryAuthorityInvalidationPort jobs, SurgeryClockPort clock) {
        return new SurgeryAuthorityInvalidationQueryService(jobs, clock);
    }
    @Bean ApplySurgeryAuthorityInvalidationUseCase applySurgeryAuthorityInvalidation(SurgeryCaseRepositoryPort cases,
            SurgeryScheduleRepositoryPort schedules, SurgeryResourceReservationPort resources, SurgeryAuthorityInvalidationPort jobs, SurgeryClockPort clock) {
        return new SurgeryAuthorityInvalidationService(cases, schedules, resources, jobs, clock);
    }
    @Bean RecordSurgeryAuthorityInvalidationRetryUseCase retrySurgeryAuthorityInvalidation(SurgeryCaseRepositoryPort cases,
            SurgeryAuthorityInvalidationPort jobs, SurgeryClockPort clock) {
        return new SurgeryAuthorityInvalidationRetryService(cases, jobs, clock);
    }
}
