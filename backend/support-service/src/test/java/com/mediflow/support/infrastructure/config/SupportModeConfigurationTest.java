package com.mediflow.support.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class SupportModeConfigurationTest {
    @Test void hostRejectsMissingModuleProfilesAndDeferredActivation() {
        var env = new MockEnvironment();
        var guard = SupportModeConfiguration.supportModuleGuard(env);
        assertThatThrownBy(() -> guard.postProcessBeanFactory(new DefaultListableBeanFactory()))
                .isInstanceOf(IllegalStateException.class);
        env.setActiveProfiles("report-module", "notification-module", "outpatient-mvp");
        assertThatCode(() -> guard.postProcessBeanFactory(new DefaultListableBeanFactory())).doesNotThrowAnyException();
        for (String feature : new String[]{"report.operational-consumer", "report.financial-publication", "notification.surgery-consumer"}) {
            String key = "mediflow." + feature + ".enabled";
            env.setProperty(key, "true");
            assertThatThrownBy(() -> guard.postProcessBeanFactory(new DefaultListableBeanFactory()))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining(feature);
            env.setProperty(key, "false");
        }
    }
}
