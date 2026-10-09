package com.mediflow.surgery.infrastructure.config;

import com.mediflow.surgery.application.port.in.*;
import com.mediflow.surgery.application.port.out.*;
import com.mediflow.surgery.application.service.AuthorizedSurgeryPreopService;
import com.mediflow.surgery.web.SurgeryPreopMutationController;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SurgeryPreopApiConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SurgeryPreopApiConfiguration.class, SurgeryPreopMutationController.class);
    private static final List<Class<?>> PORTS = List.of(SurgeryCaseRepositoryPort.class,
            UpdateChecklistItemUseCase.class, ManageSurgeryConsentUseCase.class,
            SurgeryPreopAuthorityPort.class, SurgeryClockPort.class, SurgeryUnitOfWorkPort.class);

    @Test void preop_absentFlags_hasNoBoundaryOrPermissiveAuthority() {
        runner.run(c -> { assertThat(c).doesNotHaveBean(SurgeryPreopMutationController.class);
            assertThat(c).doesNotHaveBean(RecordSurgeryPreopUseCase.class);
            assertThat(c).doesNotHaveBean(SurgeryPreopAuthorityPort.class); });
    }
    @ParameterizedTest @CsvSource({"false,false", "false,true", "true,false"})
    void preop_eitherGateDisabled_registersNothing(boolean business, boolean api) {
        runner.withPropertyValues("mediflow.features.surgery.enabled=" + business,
                "mediflow.surgery.preop.api.enabled=" + api).run(c -> {
            assertThat(c).doesNotHaveBean(SurgeryPreopMutationController.class);
            assertThat(c).doesNotHaveBean(RecordSurgeryPreopUseCase.class); });
    }
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void preop_anyMandatoryPortMissing_failsStartup(int missing) {
        var configured = enabled();
        for (int i = 0; i < PORTS.size(); i++) if (i != missing) configured = withMock(configured, PORTS.get(i));
        configured.run(c -> assertThat(c).hasFailed());
    }
    @Test void preop_explicitPorts_wireRealAuthorizedFacade() {
        var configured = enabled();
        for (var type : PORTS) configured = withMock(configured, type);
        configured.run(c -> { assertThat(c).hasSingleBean(SurgeryPreopMutationController.class);
            assertThat(c.getBean(RecordSurgeryPreopUseCase.class)).isInstanceOf(AuthorizedSurgeryPreopService.class); });
    }
    @Test void preop_testProfile_neverInstallsProductionFacade() {
        enabled().withPropertyValues("spring.profiles.active=test")
                .withBean(RecordSurgeryPreopUseCase.class, () -> mock(RecordSurgeryPreopUseCase.class))
                .run(c -> { assertThat(c).hasSingleBean(SurgeryPreopMutationController.class);
                    assertThat(c).doesNotHaveBean(SurgeryPreopAuthorityPort.class); });
    }
    private ApplicationContextRunner enabled() {
        return runner.withPropertyValues("mediflow.features.surgery.enabled=true", "mediflow.surgery.preop.api.enabled=true");
    }
    private static <T> ApplicationContextRunner withMock(ApplicationContextRunner r, Class<T> type) {
        return r.withBean(type, () -> mock(type));
    }
}
