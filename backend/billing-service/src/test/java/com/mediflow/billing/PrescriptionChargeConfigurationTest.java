package com.mediflow.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.port.in.IssuePrescriptionChargeUseCase;
import com.mediflow.billing.application.port.out.*;
import com.mediflow.billing.infrastructure.config.PrescriptionChargeConfiguration;
import com.mediflow.billing.infrastructure.messaging.consumer.PrescriptionChargeConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PrescriptionChargeConfigurationTest {
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void intake_requiresBothExplicitGates(int flags) {
        runner().withPropertyValues("mediflow.billing.ledger.enabled=" + ((flags & 1) != 0),
                "mediflow.billing.prescription-charge-consumer.enabled=" + ((flags & 2) != 0)).run(context -> {
            assertThat(context).hasNotFailed();
            if (flags == 3) assertThat(context).hasSingleBean(PrescriptionChargeConsumer.class).hasSingleBean(IssuePrescriptionChargeUseCase.class);
            else assertThat(context).doesNotHaveBean(PrescriptionChargeConsumer.class).doesNotHaveBean(IssuePrescriptionChargeUseCase.class);
        });
    }
    @Test void default_noIssuerAndNoCompetingTopology() {
        runner().run(context -> { assertThat(context).doesNotHaveBean(PrescriptionChargeConsumer.class); assertThat(context).doesNotHaveBean("prescriptionChargeTopology"); });
    }
    @Test void enabled_noCompetingRabbitListener() {
        assertThat(java.util.Arrays.stream(PrescriptionChargeConsumer.class.getDeclaredMethods()))
                .noneMatch(method -> method.isAnnotationPresent(org.springframework.amqp.rabbit.annotation.RabbitListener.class));
    }
    @Test void enabled_withoutExplicitPricePolicy_failsStartup() {
        runner().withPropertyValues("mediflow.billing.ledger.enabled=true", "mediflow.billing.prescription-charge-consumer.enabled=true",
                "mediflow.billing.prescription-charges.allowed-price-codes=").run(context -> assertThat(context).hasFailed());
    }
    @Test void policy_onlyApprovedCodes_noInventedPriceOrFallback() {
        runner().withPropertyValues("mediflow.billing.ledger.enabled=true", "mediflow.billing.prescription-charge-consumer.enabled=true").run(context -> {
            var policy = context.getBean(PrescriptionPricePolicyPort.class); policy.requireAllowed("RX-DEFAULT");
            assertThatThrownBy(() -> policy.requireAllowed("UNAPPROVED")).hasMessage("BILLING_PRESCRIPTION_PRICE_CODE_UNSUPPORTED");
        });
    }
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(PrescriptionChargeConfiguration.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                .withBean(PrescriptionChargeRepositoryPort.class, () -> mock(PrescriptionChargeRepositoryPort.class))
                .withBean(PrescriptionTerminalRepositoryPort.class, () -> mock(PrescriptionTerminalRepositoryPort.class))
                .withBean(PrescriptionChargeWirePort.class, () -> mock(PrescriptionChargeWirePort.class))
                .withBean(LedgerEventPort.class, () -> mock(LedgerEventPort.class))
                .withPropertyValues("mediflow.billing.prescription-charges.allowed-price-codes=RX-DEFAULT");
    }
}
