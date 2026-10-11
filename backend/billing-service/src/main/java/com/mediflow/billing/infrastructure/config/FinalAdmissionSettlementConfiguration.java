package com.mediflow.billing.infrastructure.config;

import com.mediflow.billing.application.port.in.FinalizeAdmissionUseCase;
import com.mediflow.billing.application.port.out.AdmissionLedgerReadPort;
import com.mediflow.billing.application.port.out.FinalAdmissionSettlementStorePort;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.application.service.FinalAdmissionSettlementService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"mediflow.billing.ledger.enabled","mediflow.billing.settlement-preview.enabled",
        "mediflow.billing.admission-charge-close.enabled","mediflow.billing.final-settlement.enabled"},havingValue="true")
public class FinalAdmissionSettlementConfiguration {
    @Bean FinalizeAdmissionUseCase finalizeAdmission(FinalAdmissionSettlementStorePort store,AdmissionLedgerReadPort ledger,
            LedgerEventPort events,PlatformTransactionManager manager) {
        var service=new FinalAdmissionSettlementService(store,ledger,events);var tx=new TransactionTemplate(manager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);tx.setTimeout(8);
        return command->{
            for(int attempt=0;attempt<3;attempt++) {
                try{return tx.execute(status->service.finalizeAdmission(command));}
                catch(RuntimeException failure){if(attempt==2 || !retryable(failure))throw failure;}
            }
            throw new IllegalStateException("Final settlement retries exhausted");
        };
    }
    private static boolean retryable(Throwable failure) {
        // Retry only aborted serialization/deadlock transactions, never business rejection,
        // unknown commit outcome, connection failure or an external payment operation.
        for(Throwable cause=failure;cause!=null;cause=cause.getCause())
            if(cause instanceof java.sql.SQLException sql && ("40001".equals(sql.getSQLState()) || "40P01".equals(sql.getSQLState())))return true;
        return false;
    }
}
