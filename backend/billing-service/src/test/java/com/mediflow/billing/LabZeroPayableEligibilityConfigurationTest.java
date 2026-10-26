package com.mediflow.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.mediflow.billing.application.port.out.LabZeroPayableEligibilityRepositoryPort;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.application.port.out.LedgerPaymentRepositoryPort;
import com.mediflow.billing.application.port.out.OutpatientQuoteRepositoryPort;
import com.mediflow.billing.infrastructure.config.LabZeroPayableEligibilityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

class LabZeroPayableEligibilityConfigurationTest {
    @Test void localLabZeroPathRequiresLedgerQuoteAndZeroFlagsTogether() {
        runner().run(context -> assertThat(context).doesNotHaveBean("grantLabZeroPayableEligibility"));
        runner().withPropertyValues("mediflow.billing.ledger.enabled=true",
                "mediflow.billing.outpatient-quotes.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean("grantLabZeroPayableEligibility"));
        runner().withPropertyValues("mediflow.billing.ledger.enabled=true",
                "mediflow.billing.zero-payable-eligibility.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean("grantLabZeroPayableEligibility"));
        runner().withPropertyValues("mediflow.billing.ledger.enabled=true",
                "mediflow.billing.outpatient-quotes.enabled=true",
                "mediflow.billing.zero-payable-eligibility.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(com.mediflow.billing.application.port.in.GrantLabZeroPayableEligibilityUseCase.class));
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(LabZeroPayableEligibilityConfiguration.class)
                .withBean(LedgerPaymentRepositoryPort.class, () -> mock(LedgerPaymentRepositoryPort.class))
                .withBean(LabZeroPayableEligibilityRepositoryPort.class, () -> mock(LabZeroPayableEligibilityRepositoryPort.class))
                .withBean(OutpatientQuoteRepositoryPort.class, () -> mock(OutpatientQuoteRepositoryPort.class))
                .withBean(LedgerEventPort.class, () -> mock(LedgerEventPort.class))
                .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class));
    }
}
