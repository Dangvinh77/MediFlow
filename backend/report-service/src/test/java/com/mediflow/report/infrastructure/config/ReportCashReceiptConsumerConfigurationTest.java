package com.mediflow.report.infrastructure.config;

import com.mediflow.report.application.port.in.ApplyCashReceiptUseCase;
import com.mediflow.report.application.port.out.CareFinanceWirePort;
import com.mediflow.report.messaging.consumer.CashReceiptConsumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ReportCashReceiptConsumerConfigurationTest {
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void intake_requiresBothExplicitGates(int flags) {
        runner().withPropertyValues("mediflow.features.care-finance-v2=" + ((flags & 1) != 0),
                "mediflow.report.cash-receipt-consumer.enabled=" + ((flags & 2) != 0)).run(context -> {
            assertThat(context).hasNotFailed();
            if (flags == 3) {
                assertThat(context).hasSingleBean(CashReceiptConsumer.class);
                assertThat(context).hasBean("reportCashReceiptTopology");
                assertThat(context).hasBean("reportCashReceiptListenerFactory");
            } else {
                assertThat(context).doesNotHaveBean(CashReceiptConsumer.class);
                assertThat(context).doesNotHaveBean("reportCashReceiptTopology");
                assertThat(context).doesNotHaveBean("reportCashReceiptListenerFactory");
            }
        });
    }
    @Test void intake_absentFlagsCreatesNoConsumer() {
        runner().run(context -> assertThat(context).doesNotHaveBean(CashReceiptConsumer.class));
    }
    @Test void topology_onlyPaymentCompletedNoRefundSettlementOrLegacyQueue() {
        var topology = new ReportCashReceiptConsumerConfiguration().reportCashReceiptTopology();
        assertThat(topology.getDeclarablesByType(Binding.class).stream()
                .filter(b -> b.getExchange().equals("mediflow.events")).map(Binding::getRoutingKey)
                .collect(Collectors.toSet())).containsExactly("payment.completed");
        var queues = topology.getDeclarablesByType(Queue.class);
        assertThat(queues).hasSize(2).allMatch(Queue::isDurable);
        assertThat(queues.stream().map(Queue::getName)).containsExactlyInAnyOrder("report.cash-receipts-v2.q", "report.cash-receipts-v2.dlq");
        assertThat(queues.stream().filter(q -> q.getName().equals("report.cash-receipts-v2.q")).findFirst().orElseThrow()
                .getArguments()).containsEntry("x-dead-letter-routing-key", "report.cash-receipts-v2.dlq");
    }
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(ReportCashReceiptConsumerConfiguration.class)
                .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
                .withBean(CareFinanceWirePort.class, () -> mock(CareFinanceWirePort.class))
                .withBean(ApplyCashReceiptUseCase.class, () -> mock(ApplyCashReceiptUseCase.class))
                .withPropertyValues("spring.rabbitmq.listener.simple.auto-startup=false");
    }
}
