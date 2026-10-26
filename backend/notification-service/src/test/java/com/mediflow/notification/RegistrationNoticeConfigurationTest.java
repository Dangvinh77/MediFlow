package com.mediflow.notification;

import com.mediflow.notification.infrastructure.config.RegistrationNoticeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class RegistrationNoticeConfigurationTest {
    @Test void defaultAndExplicitOffDoNotCreateAListenerOrQueueBindings() {
        var runner = new ApplicationContextRunner().withUserConfiguration(RegistrationNoticeConfiguration.class);
        for (var context : java.util.List.of(runner, runner.withPropertyValues(
                "mediflow.notification.outpatient-registration-consumer.enabled=false"))) {
            context.run(result -> {
                assertThat(result).hasNotFailed();
                assertThat(result).doesNotHaveBean("registrationNoticeConsumer");
                assertThat(result).doesNotHaveBean("registrationNoticeBindings");
            });
        }
    }
}
