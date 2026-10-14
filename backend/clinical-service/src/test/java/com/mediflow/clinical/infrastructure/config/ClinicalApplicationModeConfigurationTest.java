package com.mediflow.clinical.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.clinical.application.port.out.OutpatientMvpMode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ClinicalApplicationModeConfigurationTest {

    @Test
    void profileCannotBeDisabledByFalsePropertyOverride() {
        new ApplicationContextRunner()
                .withUserConfiguration(ClinicalApplicationModeConfiguration.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("outpatient-mvp"))
                .withPropertyValues("mediflow.features.outpatient-mvp.enabled=false")
                .run(context -> assertThat(context.getBean(OutpatientMvpMode.class).enabled()).isTrue());
    }

    @Test
    void propertySelectsOutpatientModeWithoutNamedProfile() {
        new ApplicationContextRunner()
                .withUserConfiguration(ClinicalApplicationModeConfiguration.class)
                .withPropertyValues("mediflow.features.outpatient-mvp.enabled=true")
                .run(context -> assertThat(context.getBean(OutpatientMvpMode.class).enabled()).isTrue());
    }

    @Test
    void compatibilityModeRemainsDefaultWhenNeitherSelectorIsActive() {
        new ApplicationContextRunner()
                .withUserConfiguration(ClinicalApplicationModeConfiguration.class)
                .run(context -> assertThat(context.getBean(OutpatientMvpMode.class).enabled()).isFalse());
    }
}
