package com.mediflow.surgery.infrastructure.config;

import com.mediflow.surgery.application.port.in.ManageSurgeryConsentUseCase;
import com.mediflow.surgery.application.port.in.RevokeSurgeryConsentUseCase;
import com.mediflow.surgery.application.port.out.*;
import com.mediflow.surgery.application.service.AuthorizedSurgeryConsentRevocationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SurgeryConsentRevocationConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SurgeryConsentRevocationConfiguration.class);
    private static final List<Class<?>> PORTS = List.of(SurgeryCaseRepositoryPort.class,
            SurgeryConsentRepositoryPort.class, ManageSurgeryConsentUseCase.class,
            SurgeryConsentRevocationAuthorityPort.class, SurgeryClockPort.class, SurgeryUnitOfWorkPort.class);

    @Test void revocation_defaultOff_hasNoFacadeOrPermissiveAuthority() {
        runner.run(c -> {
            assertThat(c).doesNotHaveBean(RevokeSurgeryConsentUseCase.class);
            assertThat(c).doesNotHaveBean(SurgeryConsentRevocationAuthorityPort.class);
        });
    }
    @ParameterizedTest @CsvSource({"false,false", "false,true", "true,false"})
    void revocation_eitherGateOff_doesNotRegisterBoundary(boolean business, boolean revocation) {
        runner.withPropertyValues("mediflow.features.surgery.enabled=" + business,
                "mediflow.surgery.consent.revocation.enabled=" + revocation)
                .run(c -> assertThat(c).doesNotHaveBean(RevokeSurgeryConsentUseCase.class));
    }
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void revocation_missingMandatoryPort_failsStartup(int missing) {
        var configured = enabled();
        for (int i = 0; i < PORTS.size(); i++) if (i != missing) configured = withMock(configured, PORTS.get(i));
        configured.run(c -> assertThat(c).hasFailed());
    }
    @Test void revocation_allPortsInstalled_registersActualAuthorizedFacade() {
        var configured = enabled();
        for (var type : PORTS) configured = withMock(configured, type);
        configured.run(c -> {
            assertThat(c).hasSingleBean(RevokeSurgeryConsentUseCase.class);
            assertThat(c.getBean(RevokeSurgeryConsentUseCase.class)).isInstanceOf(AuthorizedSurgeryConsentRevocationService.class);
        });
    }
    @Test void revocation_testProfile_doesNotInstallProductionBoundary() {
        enabled().withPropertyValues("spring.profiles.active=test")
                .run(c -> assertThat(c).doesNotHaveBean(RevokeSurgeryConsentUseCase.class));
    }
    private ApplicationContextRunner enabled() {
        return runner.withPropertyValues("mediflow.features.surgery.enabled=true", "mediflow.surgery.consent.revocation.enabled=true");
    }
    private static <T> ApplicationContextRunner withMock(ApplicationContextRunner r, Class<T> type) {
        return r.withBean(type, () -> mock(type));
    }
}
