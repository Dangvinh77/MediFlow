package com.mediflow.billing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import com.mediflow.billing.application.mapper.LedgerPaymentMapper;
import com.mediflow.billing.application.port.in.RefundLedgerPaymentUseCase;
import com.mediflow.billing.application.port.out.*;
import com.mediflow.billing.infrastructure.config.LedgerRefundConfiguration;
import com.mediflow.billing.web.LedgerRefundController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

class LedgerRefundConfigurationTest {
    @ParameterizedTest @ValueSource(ints = {0,1,2,3})
    void refunds_requireBothIndependentGates(int flags) {
        runner().withPropertyValues("mediflow.billing.ledger.enabled=" + ((flags & 1) != 0), "mediflow.billing.refunds.enabled=" + ((flags & 2) != 0)).run(context -> {
            assertThat(context).hasNotFailed();
            if (flags == 3) { assertThat(context).hasSingleBean(RefundLedgerPaymentUseCase.class); assertThat(context).hasSingleBean(LedgerRefundController.class); }
            else { assertThat(context).doesNotHaveBean(RefundLedgerPaymentUseCase.class); assertThat(context).doesNotHaveBean(LedgerRefundController.class); }
        });
    }
    @Test void defaults_noRefundApiOrUseCase() { runner().run(context -> assertThat(context).doesNotHaveBean(LedgerRefundController.class)); }
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(LedgerRefundConfiguration.class, LedgerRefundController.class)
                .withBean(LedgerPaymentRepositoryPort.class, () -> mock(LedgerPaymentRepositoryPort.class))
                .withBean(LedgerRefundRepositoryPort.class, () -> mock(LedgerRefundRepositoryPort.class))
                .withBean(LedgerEventPort.class, () -> mock(LedgerEventPort.class))
                .withBean(LedgerPaymentMapper.class, () -> mock(LedgerPaymentMapper.class))
                .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class));
    }
}
