package com.mediflow.lab.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.lab.application.port.out.LabOutpatientMvpMode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LabOutpatientMvpModeConfigurationTest {

    @Test
    void activeProfileCannotBeDisabledByFalsePropertyOverride() {
        new ApplicationContextRunner()
                .withUserConfiguration(LabOutpatientMvpModeConfiguration.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("outpatient-mvp"))
                .withPropertyValues("mediflow.features.outpatient-mvp.enabled=false")
                .run(context -> assertThat(context.getBean(LabOutpatientMvpMode.class).enabled()).isTrue());
    }

    @Test
    void propertySelectsOutpatientModeWithoutNamedProfile() {
        new ApplicationContextRunner()
                .withUserConfiguration(LabOutpatientMvpModeConfiguration.class)
                .withPropertyValues("mediflow.features.outpatient-mvp.enabled=true")
                .run(context -> assertThat(context.getBean(LabOutpatientMvpMode.class).enabled()).isTrue());
    }

    @Test
    void compatibilityModeRemainsDefaultWhenNeitherSelectorIsActive() {
        new ApplicationContextRunner()
                .withUserConfiguration(LabOutpatientMvpModeConfiguration.class)
                .run(context -> assertThat(context.getBean(LabOutpatientMvpMode.class).enabled()).isFalse());
    }
}
