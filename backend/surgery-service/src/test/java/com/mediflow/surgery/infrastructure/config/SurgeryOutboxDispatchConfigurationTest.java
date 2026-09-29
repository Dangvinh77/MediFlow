package com.mediflow.surgery.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.surgery.infrastructure.messaging.SurgeryOutboxDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SurgeryOutboxDispatchConfigurationTest {

    @Test
    void dispatcher_isAbsentWhenFeatureOrProducerGateIsOff() {
        new ApplicationContextRunner()
                .withUserConfiguration(SurgeryOutboxDispatchConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean(SurgeryOutboxDispatcher.class));

        new ApplicationContextRunner()
                .withUserConfiguration(SurgeryOutboxDispatchConfiguration.class)
                .withPropertyValues("mediflow.features.surgery.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(SurgeryOutboxDispatcher.class));

        new ApplicationContextRunner()
                .withUserConfiguration(SurgeryOutboxDispatchConfiguration.class)
                .withPropertyValues("mediflow.surgery.messaging.producer.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(SurgeryOutboxDispatcher.class));
    }
}
