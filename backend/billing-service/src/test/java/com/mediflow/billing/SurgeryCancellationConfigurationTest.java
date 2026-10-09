package com.mediflow.billing;

import com.mediflow.billing.application.port.in.ProcessSurgeryCancellationUseCase;
import com.mediflow.billing.application.port.in.GetSurgeryCancellationUseCase;
import com.mediflow.billing.application.port.out.SurgeryCancellationRepositoryPort;
import com.mediflow.billing.application.port.out.SurgeryCancellationWirePort;
import com.mediflow.billing.infrastructure.config.SurgeryCancellationConfiguration;
import com.mediflow.billing.infrastructure.config.SurgeryCancellationRecoveryWorker;
import com.mediflow.billing.messaging.consumer.SurgeryCancellationConsumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SurgeryCancellationConfigurationTest {
    @ParameterizedTest @ValueSource(ints = {0,1,2,3})
    void registration_requiresBothIssuerGates_andAddsNoCompetingListener(int flags) {
        new ApplicationContextRunner().withUserConfiguration(SurgeryCancellationConfiguration.class,
                com.mediflow.billing.web.SurgeryCancellationController.class)
                .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                .withBean(SurgeryCancellationRepositoryPort.class, () -> mock(SurgeryCancellationRepositoryPort.class))
                .withBean(SurgeryCancellationWirePort.class, () -> mock(SurgeryCancellationWirePort.class))
                .withPropertyValues("mediflow.billing.ledger.enabled=" + ((flags&1)!=0), "mediflow.billing.surgery-charge-consumer.enabled=" + ((flags&2)!=0))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    if (flags==3) {
                        assertThat(context).hasSingleBean(SurgeryCancellationConsumer.class);
                        assertThat(context).hasSingleBean(ProcessSurgeryCancellationUseCase.class);
                        assertThat(context).hasSingleBean(GetSurgeryCancellationUseCase.class);
                        assertThat(context).hasSingleBean(SurgeryCancellationRecoveryWorker.class);
                        assertThat(context).hasSingleBean(com.mediflow.billing.web.SurgeryCancellationController.class);
                    } else {
                        assertThat(context).doesNotHaveBean(SurgeryCancellationConsumer.class);
                        assertThat(context).doesNotHaveBean(SurgeryCancellationRecoveryWorker.class);
                        assertThat(context).doesNotHaveBean(com.mediflow.billing.web.SurgeryCancellationController.class);
                    }
                    assertThat(java.util.Arrays.stream(SurgeryCancellationConsumer.class.getDeclaredMethods()))
                            .noneMatch(m -> m.isAnnotationPresent(org.springframework.amqp.rabbit.annotation.RabbitListener.class));
                });
    }
}
