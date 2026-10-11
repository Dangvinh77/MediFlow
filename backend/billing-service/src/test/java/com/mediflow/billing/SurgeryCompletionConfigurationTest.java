package com.mediflow.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.port.in.ConsumeSurgeryCompletionUseCase;
import com.mediflow.billing.application.port.in.SurgeryChargeUseCase;
import com.mediflow.billing.application.port.out.SurgeryCompletionStorePort;
import com.mediflow.billing.infrastructure.config.SurgeryCompletionConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SurgeryCompletionConfigurationTest {
    @ParameterizedTest @CsvSource({"false,false,false","false,false,true","false,true,false","false,true,true",
            "true,false,false","true,false,true","true,true,false","true,true,true"})
    void completionProofRequiresEveryGate(boolean ledger,boolean planned,boolean completed) {
        new ApplicationContextRunner().withUserConfiguration(SurgeryCompletionConfiguration.class)
                .withBean(ObjectMapper.class,()->new ObjectMapper().findAndRegisterModules())
                .withBean(SurgeryCompletionStorePort.class,()->mock(SurgeryCompletionStorePort.class))
                .withBean(SurgeryChargeUseCase.class,()->mock(SurgeryChargeUseCase.class))
                .withBean(PlatformTransactionManager.class,()->mock(PlatformTransactionManager.class))
                .withPropertyValues("mediflow.billing.ledger.enabled="+ledger,"mediflow.billing.surgery-charge-consumer.enabled="+planned,
                        "mediflow.billing.surgery-completion-proof.enabled="+completed)
                .run(context->{assertThat(context).hasNotFailed();
                    if(ledger&&planned&&completed)assertThat(context).hasSingleBean(ConsumeSurgeryCompletionUseCase.class);
                    else assertThat(context).doesNotHaveBean(ConsumeSurgeryCompletionUseCase.class);});
    }
}
