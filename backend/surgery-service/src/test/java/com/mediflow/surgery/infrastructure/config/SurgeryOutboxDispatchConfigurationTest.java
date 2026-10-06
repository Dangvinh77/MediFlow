package com.mediflow.surgery.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.surgery.infrastructure.messaging.SurgeryOutboxDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryOutboxPort;
import com.mediflow.surgery.application.port.out.SurgeryEventPublisherPort;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.Instant;
import static org.mockito.Mockito.mock;

class SurgeryOutboxDispatchConfigurationTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void dispatch_requiresBusinessAndProducerButNotConsumer(int flags) {
        boolean business = (flags & 1) != 0;
        boolean consumer = (flags & 2) != 0;
        boolean producer = (flags & 4) != 0;
        new ApplicationContextRunner()
                .withUserConfiguration(SurgeryOutboxDispatchConfiguration.class)
                .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                .withBean(SurgeryOutboxPort.class, () -> mock(SurgeryOutboxPort.class))
                .withBean(RabbitTemplate.class, () -> mock(RabbitTemplate.class))
                .withBean(SurgeryClockPort.class, () -> () -> Instant.parse("2026-10-05T08:00:00Z"))
                .withPropertyValues("mediflow.features.surgery.enabled=" + business,
                        "mediflow.surgery.messaging.consumers.enabled=" + consumer,
                        "mediflow.surgery.messaging.producer.enabled=" + producer,
                        "mediflow.surgery.messaging.producer.poll-interval-ms=3600000")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    if (business && producer) {
                        assertThat(context).hasSingleBean(SurgeryOutboxDispatcher.class);
                        assertThat(context).hasSingleBean(SurgeryEventPublisherPort.class);
                        assertThat(context).hasBean("surgeryEventsExchange");
                    } else {
                        assertThat(context).doesNotHaveBean(SurgeryOutboxDispatcher.class);
                        assertThat(context).doesNotHaveBean(SurgeryEventPublisherPort.class);
                        assertThat(context).doesNotHaveBean("surgeryEventsExchange");
                    }
                });
    }

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
