package com.mediflow.billing;

import com.mediflow.billing.application.port.out.*;
import com.mediflow.billing.infrastructure.config.SurgeryChargeConfiguration;
import com.mediflow.billing.messaging.consumer.SurgeryChargeConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SurgeryChargeConfigurationTest {
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void intake_requiresBothExplicitGates(int flags) {
        runner().withPropertyValues("mediflow.billing.ledger.enabled=" + ((flags & 1) != 0),
                "mediflow.billing.surgery-charge-consumer.enabled=" + ((flags & 2) != 0)).run(context -> {
            assertThat(context).hasNotFailed();
            if (flags == 3) assertThat(context).hasSingleBean(SurgeryChargeConsumer.class);
            else assertThat(context).doesNotHaveBean(SurgeryChargeConsumer.class);
        });
    }
    @Test void default_noConsumerOrTopology() {
        runner().run(context -> { assertThat(context).doesNotHaveBean(SurgeryChargeConsumer.class); assertThat(context).doesNotHaveBean("surgeryChargeTopology"); });
    }
    @Test void enabled_handlerAddsNoCompetingQueueBindingOrListener() {
        runner().withPropertyValues("mediflow.billing.ledger.enabled=true", "mediflow.billing.surgery-charge-consumer.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(SurgeryChargeConsumer.class);
            assertThat(context).doesNotHaveBean("surgeryChargeTopology");
            assertThat(context).doesNotHaveBean("surgeryChargeListenerFactory");
            assertThat(java.util.Arrays.stream(SurgeryChargeConsumer.class.getDeclaredMethods()))
                    .noneMatch(method -> method.isAnnotationPresent(org.springframework.amqp.rabbit.annotation.RabbitListener.class));
        });
    }
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(SurgeryChargeConfiguration.class)
                .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
                .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                .withBean(SurgeryPlannedRequestRepositoryPort.class, () -> mock(SurgeryPlannedRequestRepositoryPort.class))
                .withBean(SurgeryCancellationRepositoryPort.class, () -> mock(SurgeryCancellationRepositoryPort.class))
                .withBean(SurgeryChargeWirePort.class, () -> mock(SurgeryChargeWirePort.class))
                .withBean(PriceCatalogPort.class, () -> mock(PriceCatalogPort.class))
                .withBean(LedgerEventPort.class, () -> mock(LedgerEventPort.class))
                .withPropertyValues("spring.rabbitmq.listener.simple.auto-startup=false");
    }
}
