package com.mediflow.billing;

import com.mediflow.billing.application.port.in.FindPaymentRequestQueueUseCase;
import com.mediflow.billing.application.port.out.PaymentRequestDiscoveryPort;
import com.mediflow.billing.application.port.out.PaymentRequestQueuePort;
import com.mediflow.billing.infrastructure.config.PaymentRequestDiscoveryConfiguration;
import com.mediflow.billing.web.PaymentRequestQueueController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PaymentRequestQueueConfigurationTest {
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(PaymentRequestDiscoveryConfiguration.class,PaymentRequestQueueController.class)
                .withBean(PaymentRequestDiscoveryPort.class,() -> mock(PaymentRequestDiscoveryPort.class))
                .withBean(PaymentRequestQueuePort.class,() -> mock(PaymentRequestQueuePort.class));
    }
    @Test void defaultHasNoQueueEndpointOrUseCase() {
        runner().run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(FindPaymentRequestQueueUseCase.class)
                    .doesNotHaveBean(PaymentRequestQueueController.class);
        });
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3})
    void bothExistingGatesMustBeExplicitlyEnabled(int flags) {
        runner().withPropertyValues("mediflow.billing.ledger.enabled="+((flags&1)!=0),
                "mediflow.billing.payment-request-discovery.enabled="+((flags&2)!=0)).run(context -> {
            assertThat(context).hasNotFailed();
            if (flags==3) {
                assertThat(context).hasSingleBean(FindPaymentRequestQueueUseCase.class)
                        .hasSingleBean(PaymentRequestQueueController.class);
            } else {
                assertThat(context).doesNotHaveBean(FindPaymentRequestQueueUseCase.class)
                        .doesNotHaveBean(PaymentRequestQueueController.class);
            }
        });
    }
}
