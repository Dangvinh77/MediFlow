package com.mediflow.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.port.in.CloseAdmissionChargesUseCase;
import com.mediflow.billing.application.port.out.AdmissionChargeCloseStorePort;
import com.mediflow.billing.infrastructure.config.AdmissionChargeCloseConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AdmissionChargeCloseConfigurationTest {
    @ParameterizedTest @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void onlyBothGatesCreateSingleQueueBindingAndWriter(boolean ledger, boolean close) {
        new ApplicationContextRunner().withUserConfiguration(AdmissionChargeCloseConfiguration.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(AdmissionChargeCloseStorePort.class, () -> mock(AdmissionChargeCloseStorePort.class))
                .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                .withBean("billingQueue", Queue.class, () -> new Queue("billing.q"))
                .withBean("mediflowEventsExchange", TopicExchange.class, () -> new TopicExchange("mediflow.events"))
                .withPropertyValues("mediflow.billing.ledger.enabled=" + ledger, "mediflow.billing.admission-charge-close.enabled=" + close)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    if (ledger && close) {
                        assertThat(context).hasSingleBean(CloseAdmissionChargesUseCase.class).hasSingleBean(Binding.class);
                        assertThat(context.getBean(Binding.class).getDestination()).isEqualTo("billing.q");
                        assertThat(context.getBean(Binding.class).getRoutingKey()).isEqualTo("discharge.medically.approved");
                    } else {
                        assertThat(context).doesNotHaveBean(CloseAdmissionChargesUseCase.class).doesNotHaveBean(Binding.class);
                    }
                });
    }
}
