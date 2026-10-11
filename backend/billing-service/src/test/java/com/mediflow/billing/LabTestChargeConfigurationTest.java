package com.mediflow.billing;

import com.mediflow.billing.application.port.out.LabTestChargeRepositoryPort;
import com.mediflow.billing.application.port.out.LabTestChargeWirePort;
import com.mediflow.billing.application.port.out.PriceCatalogPort;
import com.mediflow.billing.infrastructure.config.LabTestChargeConfiguration;
import com.mediflow.billing.messaging.consumer.LabTestChargeConsumer;
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

class LabTestChargeConfigurationTest {
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void intake_requiresBothExplicitGates(int flags) {
        runner().withPropertyValues("mediflow.billing.ledger.enabled=" + ((flags & 1) != 0),
                "mediflow.billing.lab-test-charge-consumer.enabled=" + ((flags & 2) != 0)).run(context -> {
            assertThat(context).hasNotFailed();
            if (flags == 3) assertThat(context).hasSingleBean(LabTestChargeConsumer.class);
            else assertThat(context).doesNotHaveBean(LabTestChargeConsumer.class);
        });
    }
    @Test void default_noConsumerOrBinding() {
        runner().run(context -> {
            assertThat(context).doesNotHaveBean(LabTestChargeConsumer.class);
            assertThat(context).doesNotHaveBean("labRequestCreatedBinding");
        });
    }
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(LabTestChargeConfiguration.class)
                .withBean(Queue.class, () -> QueueBuilder.durable("billing.q").build())
                .withBean(TopicExchange.class, () -> new TopicExchange("mediflow.events"))
                .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                .withBean(LabTestChargeRepositoryPort.class, () -> mock(LabTestChargeRepositoryPort.class))
                .withBean(LabTestChargeWirePort.class, () -> mock(LabTestChargeWirePort.class))
                .withBean(PriceCatalogPort.class, () -> mock(PriceCatalogPort.class));
    }
}
