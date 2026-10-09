package com.mediflow.report;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import com.mediflow.report.application.port.in.ApplyCashRefundUseCase;
import com.mediflow.report.application.port.out.*;
import com.mediflow.report.infrastructure.config.ReportCashRefundConsumerConfiguration;
import com.mediflow.report.messaging.consumer.CashRefundConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RefundCashConfigurationTest {
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void configuration_requiresBothGates(int flags) {
        runner().withPropertyValues("mediflow.features.care-finance-v2=" + ((flags & 1) != 0),
                "mediflow.report.cash-refund-consumer.enabled=" + ((flags & 2) != 0)).run(context -> {
            assertThat(context).hasNotFailed();
            if (flags == 3) assertThat(context).hasSingleBean(CashRefundConsumer.class);
            else assertThat(context).doesNotHaveBean(CashRefundConsumer.class);
        });
    }
    @Test void configuration_absentFlags_hasNoQueueListenerOrWorker() {
        runner().run(context -> {
            assertThat(context).doesNotHaveBean(CashRefundConsumer.class);
            assertThat(context).doesNotHaveBean(ReportCashRefundConsumerConfiguration.RefundRecoveryWorker.class);
        });
    }
    @Test void topology_refundOnly_preservesDurableDlq() {
        runner().withPropertyValues("mediflow.features.care-finance-v2=true", "mediflow.report.cash-refund-consumer.enabled=true").run(context -> {
            var topology = context.getBean("reportCashRefundTopology", Declarables.class);
            assertThat(topology.getDeclarablesByType(Binding.class).stream().filter(b -> b.getExchange().equals("mediflow.events")).map(Binding::getRoutingKey))
                    .containsExactly("payment.refunded");
            assertThat(topology.getDeclarablesByType(Queue.class)).hasSize(2).allMatch(Queue::isDurable);
        });
    }
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(ReportCashRefundConsumerConfiguration.class)
                .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
                .withBean(CareFinanceWirePort.class, () -> mock(CareFinanceWirePort.class))
                .withBean(ApplyCashRefundUseCase.class, () -> mock(ApplyCashRefundUseCase.class))
                .withBean(CashRefundStorePort.class, () -> mock(CashRefundStorePort.class))
                .withPropertyValues("spring.rabbitmq.listener.simple.auto-startup=false", "mediflow.report.cash-refund-consumer.recovery-initial-delay-ms=3600000");
    }
}
