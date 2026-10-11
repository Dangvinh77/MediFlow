package com.mediflow.billing.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.port.in.ConsumeSurgeryCompletionUseCase;
import com.mediflow.billing.application.port.in.SurgeryChargeUseCase;
import com.mediflow.billing.application.port.out.SurgeryCompletionStorePort;
import com.mediflow.billing.application.port.out.SurgeryCompletionWirePort;
import com.mediflow.billing.application.service.SurgeryCompletionProofService;
import com.mediflow.billing.infrastructure.messaging.SurgeryCompletionDecoder;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"mediflow.billing.ledger.enabled","mediflow.billing.surgery-charge-consumer.enabled",
        "mediflow.billing.surgery-completion-proof.enabled"},havingValue="true")
public class SurgeryCompletionConfiguration {
    @Bean SurgeryCompletionWirePort surgeryCompletionDecoder(ObjectMapper mapper){return new SurgeryCompletionDecoder(mapper);}
    @Bean ConsumeSurgeryCompletionUseCase consumeSurgeryCompletion(SurgeryCompletionWirePort wire,SurgeryCompletionStorePort store,
            SurgeryChargeUseCase charges,PlatformTransactionManager manager) {
        var service=new SurgeryCompletionProofService(store,charges,Clock.systemUTC());
        var tx=new TransactionTemplate(manager); tx.setTimeout(5);
        return (key,bytes)->{var command=wire.decode(key,bytes);tx.executeWithoutResult(status->service.capture(command));};
    }
}
