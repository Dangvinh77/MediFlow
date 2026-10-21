package com.mediflow.billing;

import com.mediflow.billing.application.port.out.ExamChargeRepositoryPort;
import com.mediflow.billing.application.port.out.ExamChargeWirePort;
import com.mediflow.billing.application.port.out.OutpatientCancellationRepositoryPort;
import com.mediflow.billing.application.port.out.PriceCatalogPort;
import com.mediflow.billing.infrastructure.config.ExamChargeConfiguration;
import com.mediflow.billing.messaging.consumer.ExamChargeConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ExamChargeConfigurationTest {
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void intake_requiresBothExplicitGates(int flags) {
        runner().withPropertyValues("mediflow.billing.ledger.enabled=" + ((flags & 1) != 0),
                "mediflow.billing.exam-charge-consumer.enabled=" + ((flags & 2) != 0)).run(context -> {
            assertThat(context).hasNotFailed();
            if (flags == 3) assertThat(context).hasSingleBean(ExamChargeConsumer.class);
            else assertThat(context).doesNotHaveBean(ExamChargeConsumer.class);
        });
    }
    @Test void default_noConsumer() {
        runner().run(context -> assertThat(context).doesNotHaveBean(ExamChargeConsumer.class));
    }
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(ExamChargeConfiguration.class)
                .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                .withBean(ExamChargeRepositoryPort.class, () -> mock(ExamChargeRepositoryPort.class))
                .withBean(OutpatientCancellationRepositoryPort.class, () -> mock(OutpatientCancellationRepositoryPort.class))
                .withBean(ExamChargeWirePort.class, () -> mock(ExamChargeWirePort.class))
                .withBean(PriceCatalogPort.class, () -> mock(PriceCatalogPort.class));
    }
}
