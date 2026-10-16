package com.mediflow.report.infrastructure.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mediflow.report.application.port.in.*;
import com.mediflow.report.application.port.out.*;
import com.mediflow.report.messaging.consumer.OutpatientReportFactConsumer;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ReportOutpatientConsumerConfigurationTest {
    @ParameterizedTest @ValueSource(ints={0,1,2,3})
    void requiresBothGatesAndProfile(int flags){
        runner().withInitializer(c->c.getEnvironment().setActiveProfiles("mvp-outpatient"))
                .withPropertyValues("mediflow.features.care-finance-v2="+((flags&1)!=0),
                        "mediflow.report.outpatient-consumer.enabled="+((flags&2)!=0))
                .run(c->{assertThat(c).hasNotFailed();if(flags==3)assertThat(c).hasSingleBean(OutpatientReportFactConsumer.class);
                    else assertThat(c).doesNotHaveBean(OutpatientReportFactConsumer.class);});
    }
    @Test void noProfileMeansNoNewQueueEvenWithBothGates(){
        runner().withPropertyValues("mediflow.features.care-finance-v2=true","mediflow.report.outpatient-consumer.enabled=true")
                .run(c->assertThat(c).doesNotHaveBean("reportOutpatientTopology"));
    }
    @Test void topologyContainsOnlyFiveSelectedKeysAndDurableDlq(){
        runner().withInitializer(c->c.getEnvironment().setActiveProfiles("mvp-outpatient"))
                .withPropertyValues("mediflow.features.care-finance-v2=true","mediflow.report.outpatient-consumer.enabled=true")
                .run(c->{var t=c.getBean("reportOutpatientTopology",Declarables.class);
                    assertThat(t.getDeclarablesByType(Binding.class).stream().filter(b->b.getExchange().equals("mediflow.events"))
                            .map(Binding::getRoutingKey)).containsExactly("medicalrecord.completed","lab.result.created","prescription.filled","payment.completed","payment.refunded");
                    assertThat(t.getDeclarablesByType(Queue.class)).hasSize(2).allMatch(Queue::isDurable);
                });
    }
    @Test void recoveryUsesOnlyOutpatientOriginalSelectorAndKeepsFailurePending(){
        var store=mock(CashRefundStorePort.class);var refunds=mock(ApplyCashRefundUseCase.class);UUID id=UUID.randomUUID();
        when(store.readyOutpatientOriginals(20)).thenReturn(List.of(id));
        doThrow(new IllegalStateException("offline")).when(refunds).recover(id);
        new ReportOutpatientConsumerConfiguration.OutpatientRefundRecoveryWorker(store,refunds).recover();
        verify(store).defer(id);verify(store,never()).readyOriginals(anyInt());
    }
    private ApplicationContextRunner runner(){return new ApplicationContextRunner().withUserConfiguration(ReportOutpatientConsumerConfiguration.class)
            .withBean(ConnectionFactory.class,()->mock(ConnectionFactory.class)).withBean("reportZoneId", ZoneId.class,()->ZoneId.of("Asia/Bangkok"))
            .withBean(CareFinanceWirePort.class,()->mock(CareFinanceWirePort.class))
            .withBean(ApplyOperationalContributionUseCase.class,()->mock(ApplyOperationalContributionUseCase.class))
            .withBean(ApplyCashReceiptUseCase.class,()->mock(ApplyCashReceiptUseCase.class))
            .withBean(ApplyCashRefundUseCase.class,()->mock(ApplyCashRefundUseCase.class))
            .withBean(CashRefundStorePort.class,()->mock(CashRefundStorePort.class))
            .withPropertyValues("spring.rabbitmq.listener.simple.auto-startup=false","mediflow.report.cash-refund-consumer.recovery-initial-delay-ms=3600000");}
}
