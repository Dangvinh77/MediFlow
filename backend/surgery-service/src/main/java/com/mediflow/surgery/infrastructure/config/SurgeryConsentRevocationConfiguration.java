package com.mediflow.surgery.infrastructure.config;

import com.mediflow.surgery.application.port.in.ManageSurgeryConsentUseCase;
import com.mediflow.surgery.application.port.in.RevokeSurgeryConsentUseCase;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryConsentRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryConsentRevocationAuthorityPort;
import com.mediflow.surgery.application.port.out.SurgeryUnitOfWorkPort;
import com.mediflow.surgery.application.service.AuthorizedSurgeryConsentRevocationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Internal wiring only. Missing source authority fails startup, never falls back to role checks. */
@Configuration(proxyBeanMethods = false)
@Profile("!test")
@ConditionalOnProperty(name = {"mediflow.features.surgery.enabled", "mediflow.surgery.consent.revocation.enabled"}, havingValue = "true")
public class SurgeryConsentRevocationConfiguration {
    @Bean
    RevokeSurgeryConsentUseCase revokeSurgeryConsentUseCase(SurgeryCaseRepositoryPort cases,
            SurgeryConsentRepositoryPort consents, ManageSurgeryConsentUseCase kernel,
            SurgeryConsentRevocationAuthorityPort authority, SurgeryClockPort clock, SurgeryUnitOfWorkPort transactions) {
        return new AuthorizedSurgeryConsentRevocationService(cases, consents, kernel, authority, clock, transactions);
    }
}
