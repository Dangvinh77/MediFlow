package com.mediflow.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import com.mediflow.notification.application.port.out.*;
import com.mediflow.notification.infrastructure.config.RefundNoticeConfiguration;
import com.mediflow.notification.messaging.consumer.RefundNoticeConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

class RefundNoticeConfigurationTest {
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void flags_requireBothGates(int flags) {
        runner().withPropertyValues("mediflow.notification.care-v1.enabled=" + ((flags & 1) != 0),
                "mediflow.notification.refund-consumer.enabled=" + ((flags & 2) != 0)).run(context -> {
            assertThat(context).hasNotFailed();
            if (flags == 3) assertThat(context).hasSingleBean(RefundNoticeConsumer.class);
            else assertThat(context).doesNotHaveBean(RefundNoticeConsumer.class);
        });
    }
    @Test void flags_absent_noBindingOrConsumer() { runner().run(context -> assertThat(context).doesNotHaveBean(RefundNoticeConsumer.class)); }
    @Test void topology_onlyCompletedRefundAndDurablePrivateDlq() {
        runner().withPropertyValues("mediflow.notification.care-v1.enabled=true", "mediflow.notification.refund-consumer.enabled=true").run(context -> {
            var topology = context.getBean("refundNoticeTopology", Declarables.class);
            assertThat(topology.getDeclarablesByType(Binding.class).stream().filter(b -> b.getExchange().equals("mediflow.events")).map(Binding::getRoutingKey))
                    .containsExactly("payment.refunded");
            assertThat(topology.getDeclarablesByType(Queue.class)).hasSize(2).allMatch(Queue::isDurable);
        });
    }
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(RefundNoticeConfiguration.class)
                .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
                .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                .withBean(CareNotificationRepositoryPort.class, () -> mock(CareNotificationRepositoryPort.class))
                .withBean(NotificationSourcePort.class, () -> mock(NotificationSourcePort.class))
                .withBean(RefundNoticeWirePort.class, () -> mock(RefundNoticeWirePort.class))
                .withPropertyValues("spring.rabbitmq.listener.simple.auto-startup=false");
    }
}
