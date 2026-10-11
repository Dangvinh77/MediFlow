package com.mediflow.report.infrastructure.config;

import com.mediflow.report.application.port.in.ApplyFinalSettlementRecognitionUseCase;
import com.mediflow.report.application.port.out.*;
import com.mediflow.report.messaging.consumer.FinalSettlementRecognitionConsumer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportFinalRecognitionConsumerConfigurationTest {
    private ApplicationContextRunner runner(){return new ApplicationContextRunner().withUserConfiguration(ReportFinalRecognitionConsumerConfiguration.class)
            .withBean(CareFinanceWirePort.class,()->mock(CareFinanceWirePort.class)).withBean(FinalSettlementRecognitionStorePort.class,()->mock(FinalSettlementRecognitionStorePort.class))
            .withBean(ConnectionFactory.class,()->mock(ConnectionFactory.class)).withPropertyValues("spring.rabbitmq.listener.simple.auto-startup=false");}
    @ParameterizedTest @ValueSource(ints={0,1,2,3}) void bothFlagsRequired(int flags) {
        runner().withPropertyValues("mediflow.features.care-finance-v2="+((flags&1)!=0),"mediflow.report.final-recognition-consumer.enabled="+((flags&2)!=0)).run(context->{
            assertThat(context).hasNotFailed();if(flags==3){assertThat(context).hasSingleBean(ApplyFinalSettlementRecognitionUseCase.class);assertThat(context).hasSingleBean(FinalSettlementRecognitionConsumer.class);}
            else{assertThat(context).doesNotHaveBean(FinalSettlementRecognitionConsumer.class);assertThat(context).doesNotHaveBean("reportFinalRecognitionTopology");}
        });
    }
    @Test void defaultOffCannotCreateBindingOrWorker(){runner().run(context->{assertThat(context).doesNotHaveBean("reportFinalRecognitionTopology");assertThat(context).doesNotHaveBean("finalRecognitionRecoveryWorker");});}
    @Test void exactFinalRecognitionRoutingOnlyAndDedicatedDurableDeadLetterQueue() {
        var topology=new ReportFinalRecognitionConsumerConfiguration().reportFinalRecognitionTopology();
        assertThat(topology.getDeclarablesByType(Binding.class).stream().filter(binding->binding.getExchange().equals("mediflow.events")).map(Binding::getRoutingKey)).containsExactly("settlement.recognition.completed");
        assertThat(topology.getDeclarablesByType(Queue.class)).hasSize(2).allMatch(Queue::isDurable);
    }
    @Test void recoveryContinuesOtherOperationsWhenOneStoreOrUseCaseIsUnavailable() {
        var store=mock(FinalSettlementRecognitionStorePort.class);var useCase=mock(ApplyFinalSettlementRecognitionUseCase.class);var one=UUID.randomUUID();var two=UUID.randomUUID();
        when(store.pending(20)).thenReturn(List.of(one,two));doThrow(new IllegalStateException("temporary storage outage")).when(useCase).recover(one);
        new ReportFinalRecognitionConsumerConfiguration.FinalRecognitionRecoveryWorker(store,useCase).recover();verify(store).defer(one);verify(useCase).recover(two);verify(store).defer(two);
    }
    @Test void missingCashHasBoundedPollDelayInsteadOfHotLoop(){var store=mock(FinalSettlementRecognitionStorePort.class);var useCase=mock(ApplyFinalSettlementRecognitionUseCase.class);var id=UUID.randomUUID();
        when(store.pending(20)).thenReturn(List.of(id));new ReportFinalRecognitionConsumerConfiguration.FinalRecognitionRecoveryWorker(store,useCase).recover();verify(useCase).recover(id);verify(store).defer(id);}
    @Test void pendingStorageOutageDoesNotEraseOrInvokeRecognition(){var store=mock(FinalSettlementRecognitionStorePort.class);var useCase=mock(ApplyFinalSettlementRecognitionUseCase.class);
        when(store.pending(20)).thenThrow(new IllegalStateException("storage"));assertThatCode(()->new ReportFinalRecognitionConsumerConfiguration.FinalRecognitionRecoveryWorker(store,useCase).recover()).doesNotThrowAnyException();verifyNoInteractions(useCase);}
}
