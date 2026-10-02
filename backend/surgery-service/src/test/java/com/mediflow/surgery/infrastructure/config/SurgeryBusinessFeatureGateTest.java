package com.mediflow.surgery.infrastructure.config;

import com.mediflow.surgery.application.port.in.BeginPreopUseCase;
import com.mediflow.surgery.application.port.in.CancelSurgeryUseCase;
import com.mediflow.surgery.web.SurgeryCancellationController;
import com.mediflow.surgery.web.SurgeryPreopController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class SurgeryBusinessFeatureGateTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ControllerConfiguration.class);

    @Test
    void businessControllers_areAbsentByDefaultAndAppearOnlyWhenExplicitlyEnabled() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(SurgeryPreopController.class);
            assertThat(context).doesNotHaveBean(SurgeryCancellationController.class);
        });

        contextRunner.withPropertyValues("mediflow.features.surgery.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(SurgeryPreopController.class);
                    assertThat(context).hasSingleBean(SurgeryCancellationController.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({SurgeryPreopController.class, SurgeryCancellationController.class})
    static class ControllerConfiguration {

        @Bean
        BeginPreopUseCase beginPreopUseCase() {
            return command -> null;
        }

        @Bean
        CancelSurgeryUseCase cancelSurgeryUseCase() {
            return command -> null;
        }
    }
}
