package com.mediflow.report.infrastructure.config;

import com.mediflow.report.application.mapper.BillingFinalSettlementRecognitionMapper;
import com.mediflow.report.application.port.in.ApplyFinalSettlementRecognitionUseCase;
import com.mediflow.report.application.port.out.CareFinanceWirePort;
import com.mediflow.report.application.port.out.FinalSettlementRecognitionStorePort;
import com.mediflow.report.application.service.FinalSettlementRecognitionApplicationService;
import com.mediflow.report.messaging.consumer.FinalSettlementRecognitionConsumer;
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

@Configuration(proxyBeanMethods=false)
@EnableRabbit @EnableScheduling
@org.springframework.context.annotation.Import(ReportSchedulingConfiguration.class)
@ConditionalOnProperty(name={"mediflow.features.care-finance-v2","mediflow.report.final-recognition-consumer.enabled"},havingValue="true")
@org.springframework.context.annotation.Profile("!mvp-outpatient & !outpatient-mvp")
public class ReportFinalRecognitionConsumerConfiguration {
    @Bean ApplyFinalSettlementRecognitionUseCase finalSettlementRecognition(FinalSettlementRecognitionStorePort store,
            @Value("${mediflow.report.zone-id:Asia/Bangkok}") String zone){return new FinalSettlementRecognitionApplicationService(new BillingFinalSettlementRecognitionMapper(ZoneId.of(zone)),store);}
    @Bean FinalSettlementRecognitionConsumer finalSettlementRecognitionConsumer(CareFinanceWirePort wire,ApplyFinalSettlementRecognitionUseCase useCase){return new FinalSettlementRecognitionConsumer(wire,useCase);}
    @Bean Declarables reportFinalRecognitionTopology() {
        var events=new TopicExchange("mediflow.events",true,false);var dead=new TopicExchange("mediflow.events.dlx",true,false);
        var queue=QueueBuilder.durable("report.final-recognition-v2.q").deadLetterExchange(dead.getName()).deadLetterRoutingKey("report.final-recognition-v2.dlq").build();
        var dlq=QueueBuilder.durable("report.final-recognition-v2.dlq").build();
        return new Declarables(events,dead,queue,dlq,BindingBuilder.bind(queue).to(events).with("settlement.recognition.completed"),BindingBuilder.bind(dlq).to(dead).with(dlq.getName()));
    }
    @Bean SimpleRabbitListenerContainerFactory reportFinalRecognitionListenerFactory(ConnectionFactory connection,
            @Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup) {
        var factory=new SimpleRabbitListenerContainerFactory();factory.setConnectionFactory(connection);factory.setAutoStartup(autoStartup);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);factory.setDefaultRequeueRejected(false);factory.setConcurrentConsumers(1);factory.setMaxConcurrentConsumers(2);factory.setPrefetchCount(10);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless().retryPolicy(new org.springframework.retry.policy.SimpleRetryPolicy(3,
                Map.of(AmqpRejectAndDontRequeueException.class,false),true,true)).backOffOptions(100,2,500)
                .recoverer((message,error)->{throw new AmqpRejectAndDontRequeueException("Final recognition retries exhausted");}).build());return factory;
    }
    @Bean FinalRecognitionRecoveryWorker finalRecognitionRecoveryWorker(FinalSettlementRecognitionStorePort store,ApplyFinalSettlementRecognitionUseCase useCase){return new FinalRecognitionRecoveryWorker(store,useCase);}
    public static final class FinalRecognitionRecoveryWorker {
        private final FinalSettlementRecognitionStorePort store;private final ApplyFinalSettlementRecognitionUseCase useCase;
        FinalRecognitionRecoveryWorker(FinalSettlementRecognitionStorePort store,ApplyFinalSettlementRecognitionUseCase useCase){this.store=store;this.useCase=useCase;}
        @Scheduled(scheduler = "reportTaskScheduler", fixedDelayString="${mediflow.report.final-recognition-consumer.recovery-delay-ms:5000}",initialDelayString="${mediflow.report.final-recognition-consumer.recovery-initial-delay-ms:5000}")
        public void recover() {
            try {
                for(var id:store.pending(20))try{useCase.recover(id);store.defer(id);}catch(RuntimeException unavailable){
                    try{store.defer(id);}catch(RuntimeException storageUnavailable){ /* Durable pending remains retryable. */ }
                    org.slf4j.LoggerFactory.getLogger(FinalRecognitionRecoveryWorker.class).warn("Final recognition recovery deferred");
                }
            }catch(RuntimeException unavailable){org.slf4j.LoggerFactory.getLogger(FinalRecognitionRecoveryWorker.class).warn("Final recognition storage unavailable");}
        }
    }
}
