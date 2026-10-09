package com.mediflow.surgery.infrastructure.config;

import com.mediflow.surgery.application.port.in.ManageSurgeryConsentUseCase;
import com.mediflow.surgery.application.port.in.RecordSurgeryPreopUseCase;
import com.mediflow.surgery.application.port.in.UpdateChecklistItemUseCase;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryPreopAuthorityPort;
import com.mediflow.surgery.application.port.out.SurgeryUnitOfWorkPort;
import com.mediflow.surgery.application.service.AuthorizedSurgeryPreopService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** No ConditionalOnBean suppression or positive policy fallback: missing authority must fail startup. */
@Configuration(proxyBeanMethods = false)
@Profile("!test")
@ConditionalOnProperty(name = {"mediflow.features.surgery.enabled", "mediflow.surgery.preop.api.enabled"}, havingValue = "true")
public class SurgeryPreopApiConfiguration {
    @Bean
    RecordSurgeryPreopUseCase recordSurgeryPreopUseCase(SurgeryCaseRepositoryPort cases,
            UpdateChecklistItemUseCase checklist, ManageSurgeryConsentUseCase consents,
            SurgeryPreopAuthorityPort authority, SurgeryClockPort clock, SurgeryUnitOfWorkPort transactions) {
        return new AuthorizedSurgeryPreopService(cases, checklist, consents, authority, clock, transactions);
    }
}
