package com.mediflow.billing;

import com.mediflow.billing.application.port.in.FinalizeAdmissionUseCase;
import com.mediflow.billing.application.port.out.AdmissionLedgerReadPort;
import com.mediflow.billing.application.port.out.FinalAdmissionSettlementStorePort;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.infrastructure.config.FinalAdmissionSettlementConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FinalAdmissionSettlementConfigurationTest {
    @ParameterizedTest @CsvSource({"40001,true","40P01,true","23505,false","08006,false","missing,false"})
    void retryOnlyKnownAbortedTransactionFromFreshTemplate(String state,boolean retryable) throws Exception {
        var store=mock(FinalAdmissionSettlementStorePort.class);var ledger=mock(AdmissionLedgerReadPort.class);var events=mock(LedgerEventPort.class);
        var manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(invocation->new org.springframework.transaction.support.SimpleTransactionStatus());
        var fixture=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().readTree(java.nio.file.Files.readAllBytes(
                java.nio.file.Path.of("src/test/resources/contracts/ledger-v1/settlement-recognition-completed.json")));
        var payload=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().treeToValue(fixture.path("payload"),
                com.mediflow.billing.application.event.LedgerIntegrationEvent.FinalSettlementRecognitionPayload.class);
        var failure=new org.springframework.dao.DataAccessResourceFailureException("safe test failure",new java.sql.SQLException("safe",state.equals("missing")?null:state));
        when(store.lockAndFindReplay(any())).thenThrow(failure).thenReturn(java.util.Optional.of(payload));
        new ApplicationContextRunner().withUserConfiguration(FinalAdmissionSettlementConfiguration.class)
                .withBean(FinalAdmissionSettlementStorePort.class,()->store).withBean(AdmissionLedgerReadPort.class,()->ledger)
                .withBean(LedgerEventPort.class,()->events).withBean(PlatformTransactionManager.class,()->manager)
                .withPropertyValues("mediflow.billing.ledger.enabled=true","mediflow.billing.settlement-preview.enabled=true",
                        "mediflow.billing.admission-charge-close.enabled=true","mediflow.billing.final-settlement.enabled=true")
                .run(context->{var command=new com.mediflow.billing.application.dto.command.FinalizeAdmissionCommand(payload.accountId(),java.util.UUID.randomUUID(),"retry");
                    if(retryable)assertThat(context.getBean(FinalizeAdmissionUseCase.class).finalizeAdmission(command)).isEqualTo(payload);
                    else org.assertj.core.api.Assertions.assertThatThrownBy(()->context.getBean(FinalizeAdmissionUseCase.class).finalizeAdmission(command)).isSameAs(failure);
                });
        verify(manager,times(retryable?2:1)).getTransaction(any());verify(manager).rollback(any());verify(manager,times(retryable?1:0)).commit(any());
        verifyNoInteractions(ledger,events);
    }
    @Test void knownAbortRetryStopsAfterThreeFreshTransactions() {
        var store=mock(FinalAdmissionSettlementStorePort.class);var manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(invocation->new org.springframework.transaction.support.SimpleTransactionStatus());
        when(store.lockAndFindReplay(any())).thenThrow(new org.springframework.dao.CannotSerializeTransactionException("test contention",new java.sql.SQLException("safe","40001")));
        new ApplicationContextRunner().withUserConfiguration(FinalAdmissionSettlementConfiguration.class)
                .withBean(FinalAdmissionSettlementStorePort.class,()->store).withBean(AdmissionLedgerReadPort.class,()->mock(AdmissionLedgerReadPort.class))
                .withBean(LedgerEventPort.class,()->mock(LedgerEventPort.class)).withBean(PlatformTransactionManager.class,()->manager)
                .withPropertyValues("mediflow.billing.ledger.enabled=true","mediflow.billing.settlement-preview.enabled=true",
                        "mediflow.billing.admission-charge-close.enabled=true","mediflow.billing.final-settlement.enabled=true")
                .run(context->org.assertj.core.api.Assertions.assertThatThrownBy(()->context.getBean(FinalizeAdmissionUseCase.class).finalizeAdmission(
                        new com.mediflow.billing.application.dto.command.FinalizeAdmissionCommand(java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),"retry")))
                        .isInstanceOf(org.springframework.dao.CannotSerializeTransactionException.class));
        verify(manager,times(3)).getTransaction(any());verify(manager,times(3)).rollback(any());verify(manager,never()).commit(any());
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15})
    void allFourGatesRequired(int mask) {
        new ApplicationContextRunner().withUserConfiguration(FinalAdmissionSettlementConfiguration.class)
                .withBean(FinalAdmissionSettlementStorePort.class,()->mock(FinalAdmissionSettlementStorePort.class))
                .withBean(AdmissionLedgerReadPort.class,()->mock(AdmissionLedgerReadPort.class))
                .withBean(LedgerEventPort.class,()->mock(LedgerEventPort.class))
                .withBean(PlatformTransactionManager.class,()->mock(PlatformTransactionManager.class))
                .withPropertyValues("mediflow.billing.ledger.enabled="+((mask&1)!=0),"mediflow.billing.settlement-preview.enabled="+((mask&2)!=0),
                        "mediflow.billing.admission-charge-close.enabled="+((mask&4)!=0),"mediflow.billing.final-settlement.enabled="+((mask&8)!=0))
                .run(context->{assertThat(context).hasNotFailed();if(mask==15)assertThat(context).hasSingleBean(FinalizeAdmissionUseCase.class);
                    else assertThat(context).doesNotHaveBean(FinalizeAdmissionUseCase.class);});
    }
}
