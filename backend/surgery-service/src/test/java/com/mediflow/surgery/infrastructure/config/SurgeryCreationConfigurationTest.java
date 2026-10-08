package com.mediflow.surgery.infrastructure.config;

import com.mediflow.surgery.application.port.in.CreateSurgeryCaseUseCase;
import com.mediflow.surgery.application.port.out.*;
import com.mediflow.surgery.application.service.SurgeryCreationApplicationService;
import com.mediflow.surgery.web.SurgeryCreationController;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SurgeryCreationConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SurgeryCreationConfiguration.class, SurgeryCreationController.class);
    private static final List<Class<?>> PORTS = List.of(SurgeryCaseRepositoryPort.class,
            SurgeryChecklistRepositoryPort.class, SurgeryCreationReceiptPort.class,
            SurgeryCreationAuthorityPort.class, SurgeryCareEventCapturePort.class,
            SurgeryClockPort.class, SurgeryUnitOfWorkPort.class);

    @Test void creation_missingFlags_hasNoControllerUseCaseOrAuthorityFallback() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(SurgeryCreationController.class);
            assertThat(context).doesNotHaveBean(CreateSurgeryCaseUseCase.class);
            assertThat(context).doesNotHaveBean(SurgeryCreationAuthorityPort.class);
        });
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false"})
    void creation_eitherFlagOff_hasNoBoundaryOrUseCase(boolean business, boolean creation) {
        runner.withPropertyValues("mediflow.features.surgery.enabled=" + business,
                "mediflow.surgery.creation.api.enabled=" + creation).run(context -> {
                    assertThat(context).doesNotHaveBean(SurgeryCreationController.class);
                    assertThat(context).doesNotHaveBean(CreateSurgeryCaseUseCase.class);
                });
    }

    @Test void creation_enabledWithoutDependencies_failsStartup() {
        enabled().run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6})
    void creation_anyMandatoryPortMissing_failsStartup(int missing) {
        var configured = enabled();
        for (int i = 0; i < PORTS.size(); i++) {
            if (i != missing) configured = withMock(configured, PORTS.get(i));
        }
        configured.run(context -> assertThat(context).hasFailed());
    }

    @Test void creation_explicitAuthorityAndAllPorts_wiresRealKernelOnly() {
        var configured = enabled();
        for (var port : PORTS) configured = withMock(configured, port);
        configured.run(context -> {
            assertThat(context).hasSingleBean(SurgeryCreationController.class);
            assertThat(context).hasSingleBean(CreateSurgeryCaseUseCase.class);
            assertThat(context.getBean(CreateSurgeryCaseUseCase.class)).isInstanceOf(SurgeryCreationApplicationService.class);
            assertThat(context).hasSingleBean(SurgeryCreationAuthorityPort.class);
        });
    }

    @Test void creation_testProfileNeverInstallsProductionKernel() {
        enabled().withPropertyValues("spring.profiles.active=test")
                .withBean(CreateSurgeryCaseUseCase.class, () -> mock(CreateSurgeryCaseUseCase.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(SurgeryCreationController.class);
                    assertThat(context).doesNotHaveBean(SurgeryCreationApplicationService.class);
                    assertThat(context).doesNotHaveBean(SurgeryCreationAuthorityPort.class);
                });
    }

    private ApplicationContextRunner enabled() {
        return runner.withPropertyValues("mediflow.features.surgery.enabled=true", "mediflow.surgery.creation.api.enabled=true");
    }

    private static <T> ApplicationContextRunner withMock(ApplicationContextRunner runner, Class<T> type) {
        return runner.withBean(type, () -> mock(type));
    }
}
