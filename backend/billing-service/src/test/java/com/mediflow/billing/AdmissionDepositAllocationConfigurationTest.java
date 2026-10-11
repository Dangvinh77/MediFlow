package com.mediflow.billing;

import com.mediflow.billing.application.port.in.AllocateAdmissionDepositUseCase;
import com.mediflow.billing.application.port.out.AdmissionDepositAllocationStorePort;
import com.mediflow.billing.application.port.out.AdmissionLedgerReadPort;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.infrastructure.config.AdmissionDepositAllocationConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AdmissionDepositAllocationConfigurationTest {
    @ParameterizedTest @CsvSource({"false,false,false","false,false,true","false,true,false","false,true,true",
            "true,false,false","true,false,true","true,true,false","true,true,true"})
    void allThreeGatesRequired(boolean ledger,boolean preview,boolean allocation) {
        new ApplicationContextRunner().withUserConfiguration(AdmissionDepositAllocationConfiguration.class)
                .withBean(AdmissionDepositAllocationStorePort.class,()->mock(AdmissionDepositAllocationStorePort.class))
                .withBean(AdmissionLedgerReadPort.class,()->mock(AdmissionLedgerReadPort.class))
                .withBean(LedgerEventPort.class,()->mock(LedgerEventPort.class))
                .withBean(PlatformTransactionManager.class,()->mock(PlatformTransactionManager.class))
                .withPropertyValues("mediflow.billing.ledger.enabled="+ledger,"mediflow.billing.settlement-preview.enabled="+preview,
                        "mediflow.billing.deposit-allocation.enabled="+allocation)
                .run(context->{
                    assertThat(context).hasNotFailed();
                    if(ledger&&preview&&allocation)assertThat(context).hasSingleBean(AllocateAdmissionDepositUseCase.class);
                    else assertThat(context).doesNotHaveBean(AllocateAdmissionDepositUseCase.class);
                });
    }
}
