package com.mediflow.billing;

import com.mediflow.billing.application.port.out.AdmissionDepositRequestRepositoryPort;
import com.mediflow.billing.application.port.out.AdmissionDepositRequestWirePort;
import com.mediflow.billing.infrastructure.config.AdmissionDepositRequestConfiguration;
import com.mediflow.billing.messaging.consumer.AdmissionDepositRequestConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AdmissionDepositRequestConfigurationTest {
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void intake_requiresBothExplicitGates(int flags) {
        runner().withPropertyValues("mediflow.billing.ledger.enabled=" + ((flags & 1) != 0),
                "mediflow.billing.admission-deposit-consumer.enabled=" + ((flags & 2) != 0)).run(context -> {
            assertThat(context).hasNotFailed();
            if (flags == 3) assertThat(context).hasSingleBean(AdmissionDepositRequestConsumer.class);
            else assertThat(context).doesNotHaveBean(AdmissionDepositRequestConsumer.class);
        });
    }
    @Test void default_noConsumerOrBinding() {
        runner().run(context -> {
            assertThat(context).doesNotHaveBean(AdmissionDepositRequestConsumer.class);
            assertThat(context).doesNotHaveBean("admissionDepositRequestedBinding");
        });
    }
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(AdmissionDepositRequestConfiguration.class)
                .withBean(Queue.class, () -> QueueBuilder.durable("billing.q").build())
                .withBean(TopicExchange.class, () -> new TopicExchange("mediflow.events"))
                .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                .withBean(AdmissionDepositRequestRepositoryPort.class, () -> mock(AdmissionDepositRequestRepositoryPort.class))
                .withBean(AdmissionDepositRequestWirePort.class, () -> mock(AdmissionDepositRequestWirePort.class));
    }
}
