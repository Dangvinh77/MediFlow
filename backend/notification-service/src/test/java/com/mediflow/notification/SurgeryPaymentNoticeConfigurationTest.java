package com.mediflow.notification;

import com.mediflow.notification.application.port.out.*;
import com.mediflow.notification.infrastructure.config.SurgeryPaymentNoticeConfiguration;
import com.mediflow.notification.messaging.consumer.SurgeryPaymentNoticeConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SurgeryPaymentNoticeConfigurationTest {
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void requiresBothExplicitGates(int flags) {
        runner().withPropertyValues("mediflow.notification.care-v1.enabled=" + ((flags & 1) != 0),
                "mediflow.notification.surgery-payment-request-consumer.enabled=" + ((flags & 2) != 0)).run(context -> {
            assertThat(context).hasNotFailed();
            if (flags == 3) assertThat(context).hasSingleBean(SurgeryPaymentNoticeConsumer.class);
            else assertThat(context).doesNotHaveBean(SurgeryPaymentNoticeConsumer.class);
        });
    }
    @Test void absentFlags_noBindingOrConsumer() { runner().run(context -> assertThat(context).doesNotHaveBean(SurgeryPaymentNoticeConsumer.class)); }
    @Test void topology_onlyInvoiceCreatedDurableAndSeparate() {
        runner().withPropertyValues("mediflow.notification.care-v1.enabled=true", "mediflow.notification.surgery-payment-request-consumer.enabled=true").run(context -> {
            var topology = context.getBean("surgeryPaymentNoticeTopology", Declarables.class);
            assertThat(topology.getDeclarablesByType(Binding.class).stream().filter(b -> b.getExchange().equals("mediflow.events")).map(Binding::getRoutingKey)).containsExactly("invoice.created");
            assertThat(topology.getDeclarablesByType(Queue.class)).hasSize(2).allMatch(Queue::isDurable);
        });
    }
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(SurgeryPaymentNoticeConfiguration.class)
                .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class)).withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                .withBean(CareNotificationRepositoryPort.class, () -> mock(CareNotificationRepositoryPort.class))
                .withBean(NotificationSourcePort.class, () -> mock(NotificationSourcePort.class))
                .withBean(SurgeryPaymentNoticeWirePort.class, () -> mock(SurgeryPaymentNoticeWirePort.class))
                .withPropertyValues("spring.rabbitmq.listener.simple.auto-startup=false");
    }
}
