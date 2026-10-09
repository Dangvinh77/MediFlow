package com.mediflow.report.infrastructure.config;

import java.util.Map;
import com.mediflow.report.application.port.in.ApplyCashRefundUseCase;
import com.mediflow.report.application.port.out.*;
import com.mediflow.report.messaging.consumer.CashRefundConsumer;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.*;

@Configuration(proxyBeanMethods = false)
@EnableRabbit @EnableScheduling
@ConditionalOnProperty(name = {"mediflow.features.care-finance-v2", "mediflow.report.cash-refund-consumer.enabled"}, havingValue = "true")
public class ReportCashRefundConsumerConfiguration {
    @Bean Declarables reportCashRefundTopology() {
        var events = new TopicExchange("mediflow.events", true, false); var dead = new TopicExchange("mediflow.events.dlx", true, false);
        var queue = QueueBuilder.durable("report.cash-refunds-v2.q").deadLetterExchange(dead.getName()).deadLetterRoutingKey("report.cash-refunds-v2.dlq").build();
        var dlq = QueueBuilder.durable("report.cash-refunds-v2.dlq").build();
        return new Declarables(events, dead, queue, dlq, BindingBuilder.bind(queue).to(events).with("payment.refunded"), BindingBuilder.bind(dlq).to(dead).with(dlq.getName()));
    }
    @Bean CashRefundConsumer cashRefundConsumer(CareFinanceWirePort decoder, ApplyCashRefundUseCase refunds) { return new CashRefundConsumer(decoder, refunds); }
    @Bean RefundRecoveryWorker refundRecoveryWorker(CashRefundStorePort store, ApplyCashRefundUseCase refunds) { return new RefundRecoveryWorker(store, refunds); }
    @Bean SimpleRabbitListenerContainerFactory reportCashRefundListenerFactory(ConnectionFactory connection,
            @Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup) {
        var factory = new SimpleRabbitListenerContainerFactory(); factory.setConnectionFactory(connection); factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setAutoStartup(autoStartup); factory.setDefaultRequeueRejected(false); factory.setConcurrentConsumers(1); factory.setMaxConcurrentConsumers(2); factory.setPrefetchCount(10);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(new org.springframework.retry.policy.SimpleRetryPolicy(3, Map.of(AmqpRejectAndDontRequeueException.class, false), true, true))
                .backOffOptions(100, 2, 500).recoverer((message, error) -> { throw new AmqpRejectAndDontRequeueException("Cash refund retries exhausted"); }).build());
        return factory;
    }
    public static final class RefundRecoveryWorker {
        private final CashRefundStorePort store; private final ApplyCashRefundUseCase refunds;
        RefundRecoveryWorker(CashRefundStorePort store, ApplyCashRefundUseCase refunds) { this.store = store; this.refunds = refunds; }
        @Scheduled(fixedDelayString = "${mediflow.report.cash-refund-consumer.recovery-delay-ms:5000}",
                initialDelayString = "${mediflow.report.cash-refund-consumer.recovery-initial-delay-ms:5000}")
        public void recover() {
            try {
                for (var original : store.readyOriginals(20)) {
                    try { refunds.recover(original); }
                    catch (RuntimeException unavailable) {
                        try { store.defer(original); }
                        catch (RuntimeException storageUnavailable) { /* Next bounded poll retries; never ACK or erase pending evidence. */ }
                        LoggerFactory.getLogger(RefundRecoveryWorker.class).warn("Cash refund recovery deferred");
                    }
                }
            } catch (RuntimeException unavailable) { LoggerFactory.getLogger(RefundRecoveryWorker.class).warn("Cash refund recovery storage unavailable"); }
        }
    }
}
