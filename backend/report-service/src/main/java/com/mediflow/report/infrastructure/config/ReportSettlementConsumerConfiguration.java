package com.mediflow.report.infrastructure.config;

import java.time.ZoneId;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.mapper.BillingSettlementSnapshotMapper;
import com.mediflow.report.application.port.in.ApplySettlementSnapshotUseCase;
import com.mediflow.report.application.port.out.CareFinanceWirePort;
import com.mediflow.report.application.service.SettlementSnapshotApplicationService;
import com.mediflow.report.infrastructure.persistence.adapter.SettlementReceivableStoreAdapter;
import com.mediflow.report.messaging.consumer.SettlementSnapshotConsumer;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
@EnableRabbit
@ConditionalOnProperty(name = {"mediflow.features.care-finance-v2", "mediflow.report.settlement-consumer.enabled"}, havingValue = "true")
@org.springframework.context.annotation.Profile("!mvp-outpatient & !outpatient-mvp")
public class ReportSettlementConsumerConfiguration {
    @Bean ApplySettlementSnapshotUseCase settlementSnapshots(@org.springframework.beans.factory.annotation.Qualifier("reportJdbcTemplate") JdbcTemplate jdbc, ObjectMapper mapper,
            @Value("${mediflow.report.zone-id:Asia/Bangkok}") String zone) {
        return new SettlementSnapshotApplicationService(new BillingSettlementSnapshotMapper(ZoneId.of(zone)),
                new SettlementReceivableStoreAdapter(jdbc, mapper));
    }
    @Bean SettlementSnapshotConsumer settlementSnapshotConsumer(CareFinanceWirePort decoder, ApplySettlementSnapshotUseCase snapshots) {
        return new SettlementSnapshotConsumer(decoder, snapshots);
    }
    @Bean Declarables reportSettlementTopology() {
        var events = new TopicExchange("mediflow.events", true, false);
        var dead = new TopicExchange("mediflow.events.dlx", true, false);
        var queue = QueueBuilder.durable("report.settlement-snapshots-v2.q").deadLetterExchange(dead.getName())
                .deadLetterRoutingKey("report.settlement-snapshots-v2.dlq").build();
        var dlq = QueueBuilder.durable("report.settlement-snapshots-v2.dlq").build();
        return new Declarables(events, dead, queue, dlq,
                BindingBuilder.bind(queue).to(events).with("settlement.reconciled"), BindingBuilder.bind(dlq).to(dead).with(dlq.getName()));
    }
    @Bean SimpleRabbitListenerContainerFactory reportSettlementListenerFactory(ConnectionFactory connection,
            @Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connection); factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setAutoStartup(autoStartup); factory.setDefaultRequeueRejected(false);
        factory.setConcurrentConsumers(1); factory.setMaxConcurrentConsumers(2); factory.setPrefetchCount(10);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(new org.springframework.retry.policy.SimpleRetryPolicy(3,
                        Map.of(AmqpRejectAndDontRequeueException.class, false), true, true))
                .backOffOptions(100, 2, 500).recoverer((message, error) -> {
                    throw new AmqpRejectAndDontRequeueException("Settlement snapshot retries exhausted");
                }).build());
        return factory;
    }
}
