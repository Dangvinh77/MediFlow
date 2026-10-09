package com.mediflow.notification;

import com.mediflow.notification.application.port.out.CareNotificationRepositoryPort;
import com.mediflow.notification.application.port.out.SurgeryNoticeStatePort;
import com.mediflow.notification.application.port.out.SurgeryNoticeWirePort;
import com.mediflow.notification.infrastructure.config.SurgeryNoticeConfiguration;
import com.mediflow.notification.messaging.consumer.SurgeryNoticeConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SurgeryNoticeConfigurationTest {
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void intake_requiresBothGates(int flags) {
        runner().withPropertyValues("mediflow.notification.care-v1.enabled=" + ((flags & 1) != 0),
                "mediflow.notification.surgery-consumer.enabled=" + ((flags & 2) != 0)).run(context -> {
            assertThat(context).hasNotFailed();
            if (flags == 3) { assertThat(context).hasSingleBean(SurgeryNoticeConsumer.class); assertThat(context).hasBean("surgeryNoticeTopology"); }
            else { assertThat(context).doesNotHaveBean(SurgeryNoticeConsumer.class); assertThat(context).doesNotHaveBean("surgeryNoticeTopology"); }
        });
    }
    @Test void intake_absentFlagsCreatesNoConsumer() {
        runner().run(context -> assertThat(context).doesNotHaveBean(SurgeryNoticeConsumer.class));
    }
    @Test void topology_exactFourKeysAndPrivateDurableQueues() {
        runner().withPropertyValues("mediflow.notification.care-v1.enabled=true", "mediflow.notification.surgery-consumer.enabled=true")
                .run(context -> {
                    var topology = context.getBean("surgeryNoticeTopology", org.springframework.amqp.core.Declarables.class);
                    assertThat(topology.getDeclarablesByType(Binding.class).stream().filter(b -> b.getExchange().equals("mediflow.events")).map(Binding::getRoutingKey))
                            .containsExactlyInAnyOrder("surgery.ready", "surgery.readiness.invalidated", "surgery.cancelled", "surgery.completed");
                    assertThat(topology.getDeclarablesByType(org.springframework.amqp.core.Queue.class))
                            .hasSize(2).allMatch(org.springframework.amqp.core.Queue::isDurable);
                });
    }
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(SurgeryNoticeConfiguration.class)
                .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
                .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                .withBean(CareNotificationRepositoryPort.class, () -> mock(CareNotificationRepositoryPort.class))
                .withBean(SurgeryNoticeStatePort.class, () -> mock(SurgeryNoticeStatePort.class))
                .withBean(SurgeryNoticeWirePort.class, () -> mock(SurgeryNoticeWirePort.class))
                .withPropertyValues("spring.rabbitmq.listener.simple.auto-startup=false");
    }
}
