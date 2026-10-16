package com.mediflow.report.infrastructure.config;

import com.mediflow.report.application.port.in.*;
import com.mediflow.report.application.port.out.CareFinanceWirePort;
import com.mediflow.report.application.port.out.CashRefundStorePort;
import com.mediflow.report.application.service.OutpatientReportFactReceiver;
import com.mediflow.report.messaging.consumer.OutpatientReportFactConsumer;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.*;

/** Separate default-off intake; existing queues/backlogs are never deleted or repurposed. */
@Configuration(proxyBeanMethods=false)
@Profile("mvp-outpatient | outpatient-mvp")
@ConditionalOnProperty(name={"mediflow.features.care-finance-v2","mediflow.report.outpatient-consumer.enabled"},havingValue="true")
@EnableRabbit @EnableScheduling
@org.springframework.context.annotation.Import(ReportSchedulingConfiguration.class)
public class ReportOutpatientConsumerConfiguration {
    @Bean Declarables reportOutpatientTopology() {
        var events=new TopicExchange("mediflow.events",true,false);
        var dead=new TopicExchange("mediflow.events.dlx",true,false);
        var queue=QueueBuilder.durable("report.outpatient-v1.q").deadLetterExchange(dead.getName())
                .deadLetterRoutingKey("report.outpatient-v1.dlq").build();
        var dlq=QueueBuilder.durable("report.outpatient-v1.dlq").build();
        var topology=new java.util.ArrayList<Declarable>(java.util.List.of(events,dead,queue,dlq));
        for(String key:java.util.List.of("medicalrecord.completed","lab.result.created","prescription.filled","payment.completed","payment.refunded"))
            topology.add(BindingBuilder.bind(queue).to(events).with(key));
        topology.add(BindingBuilder.bind(dlq).to(dead).with(dlq.getName()));
        return new Declarables(topology);
    }
    @Bean ReceiveOutpatientReportFactUseCase outpatientReportReceiver(ApplyOperationalContributionUseCase operations,
            ApplyCashReceiptUseCase cash,ApplyCashRefundUseCase refunds,@org.springframework.beans.factory.annotation.Qualifier("reportZoneId") ZoneId reportZoneId) {
        return new OutpatientReportFactReceiver(operations,cash,refunds,reportZoneId);
    }
    @Bean OutpatientReportFactConsumer outpatientReportConsumer(CareFinanceWirePort wire,ReceiveOutpatientReportFactUseCase receiver) {
        return new OutpatientReportFactConsumer(wire,receiver);
    }
    @Bean OutpatientRefundRecoveryWorker outpatientRefundRecoveryWorker(CashRefundStorePort store,ApplyCashRefundUseCase refunds) {
        return new OutpatientRefundRecoveryWorker(store,refunds);
    }
    @Bean SimpleRabbitListenerContainerFactory reportOutpatientListenerFactory(ConnectionFactory connection,
            @Value("${spring.rabbitmq.listener.simple.auto-startup:true}")boolean startup) {
        var factory=new SimpleRabbitListenerContainerFactory();factory.setConnectionFactory(connection);
        factory.setAutoStartup(startup);factory.setDefaultRequeueRejected(false);
        factory.setConcurrentConsumers(1);factory.setMaxConcurrentConsumers(2);factory.setPrefetchCount(10);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless().retryPolicy(new org.springframework.retry.policy.SimpleRetryPolicy(
                3,Map.of(AmqpRejectAndDontRequeueException.class,false),true,true)).backOffOptions(100,2,500)
                .recoverer((message,error)->{throw new AmqpRejectAndDontRequeueException("Outpatient report retries exhausted");}).build());
        return factory;
    }
    public static final class OutpatientRefundRecoveryWorker {
        private final CashRefundStorePort store;private final ApplyCashRefundUseCase refunds;
        OutpatientRefundRecoveryWorker(CashRefundStorePort store,ApplyCashRefundUseCase refunds){this.store=store;this.refunds=refunds;}
        @Scheduled(scheduler = "reportTaskScheduler", fixedDelayString="${mediflow.report.cash-refund-consumer.recovery-delay-ms:5000}",
                initialDelayString="${mediflow.report.cash-refund-consumer.recovery-initial-delay-ms:5000}")
        public void recover(){
            try{for(var id:store.readyOutpatientOriginals(20)){
                try{refunds.recover(id);}catch(RuntimeException unavailable){
                    try{store.defer(id);}catch(RuntimeException ignored){/* Durable pending evidence remains for the next poll. */}
                }
            }}catch(RuntimeException unavailable){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Outpatient refund recovery deferred");}
        }
    }
}
