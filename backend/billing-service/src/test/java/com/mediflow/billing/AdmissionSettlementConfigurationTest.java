package com.mediflow.billing;

import com.mediflow.billing.application.port.out.AdmissionSettlementRepositoryPort;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.application.port.out.ProcessedEventPort;
import com.mediflow.billing.infrastructure.config.AdmissionSettlementConfiguration;
import com.mediflow.billing.messaging.consumer.DischargeApprovalConsumer;
import com.mediflow.billing.web.SettlementController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AdmissionSettlementConfigurationTest {
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void intake_requiresBothExplicitGates(int flags) {
        runner().withPropertyValues("mediflow.billing.ledger.enabled=" + ((flags & 1) != 0),
                "mediflow.billing.settlement.enabled=" + ((flags & 2) != 0)).run(context -> {
            assertThat(context).hasNotFailed();
            if (flags == 3) {
                assertThat(context).hasSingleBean(DischargeApprovalConsumer.class);
                assertThat(context).hasSingleBean(SettlementController.class);
            } else {
                assertThat(context).doesNotHaveBean(DischargeApprovalConsumer.class);
                assertThat(context).doesNotHaveBean(SettlementController.class);
            }
        });
    }
    @Test void default_noConsumerControllerOrBinding() {
        runner().run(context -> {
            assertThat(context).doesNotHaveBean(DischargeApprovalConsumer.class);
            assertThat(context).doesNotHaveBean(SettlementController.class);
            assertThat(context).doesNotHaveBean("dischargeMedicallyApprovedBinding");
        });
    }
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(AdmissionSettlementConfiguration.class, SettlementController.class)
                .withBean(Queue.class, () -> QueueBuilder.durable("billing.q").build())
                .withBean(TopicExchange.class, () -> new TopicExchange("mediflow.events"))
                .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                .withBean(AdmissionSettlementRepositoryPort.class, () -> mock(AdmissionSettlementRepositoryPort.class))
                .withBean(LedgerEventPort.class, () -> mock(LedgerEventPort.class))
                .withBean(ProcessedEventPort.class, () -> mock(ProcessedEventPort.class))
                .withBean(ObjectMapper.class, ObjectMapper::new);
    }
}
