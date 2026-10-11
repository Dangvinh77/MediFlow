package com.mediflow.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.port.in.ReconcileAdmissionUseCase;
import com.mediflow.billing.application.port.out.AdmissionLedgerReadPort;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.infrastructure.config.AdmissionReconciliationConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AdmissionReconciliationConfigurationTest {
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(AdmissionReconciliationConfiguration.class)
                .withBean(AdmissionLedgerReadPort.class,()->mock(AdmissionLedgerReadPort.class))
                .withBean(LedgerEventPort.class,()->mock(LedgerEventPort.class))
                .withBean(JdbcTemplate.class,()->mock(JdbcTemplate.class)).withBean(ObjectMapper.class,()->new ObjectMapper())
                .withBean(PlatformTransactionManager.class,()->mock(PlatformTransactionManager.class));
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5,6,7})
    void configuration_requiresAllThreeGates(int flags) {
        runner().withPropertyValues("mediflow.billing.ledger.enabled="+((flags&1)!=0),
                "mediflow.billing.settlement-preview.enabled="+((flags&2)!=0),"mediflow.billing.reconciliation.enabled="+((flags&4)!=0)).run(context->{
            assertThat(context).hasNotFailed();
            if(flags==7) assertThat(context).hasSingleBean(ReconcileAdmissionUseCase.class);
            else assertThat(context).doesNotHaveBean(ReconcileAdmissionUseCase.class);
        });
    }
    @Test void configuration_absentFlags_remainsDisabled() { runner().run(context->assertThat(context).doesNotHaveBean(ReconcileAdmissionUseCase.class)); }
}
