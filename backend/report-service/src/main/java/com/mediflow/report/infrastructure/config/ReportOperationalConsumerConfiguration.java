package com.mediflow.report.infrastructure.config;

import com.mediflow.report.application.port.in.ApplyOperationalContributionUseCase;
import com.mediflow.report.application.port.in.ProjectAdmissionReportEvidenceUseCase;
import com.mediflow.report.application.port.in.ReceiveOperationalReportFactUseCase;
import com.mediflow.report.application.port.out.CareFinanceWirePort;
import com.mediflow.report.application.service.OperationalReportFactReceiver;
import com.mediflow.report.messaging.consumer.OperationalReportFactConsumer;
import java.time.ZoneId;
import java.util.Map;
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

/** Separate opt-in intake; neither legacy bindings nor accepted read publications are changed. */
@Configuration(proxyBeanMethods = false)
@EnableRabbit
@ConditionalOnProperty(name = {"mediflow.features.care-finance-v2",
        "mediflow.report.operational-consumer.enabled"}, havingValue = "true")
public class ReportOperationalConsumerConfiguration {
    @Bean
    Declarables reportOperationalTopology() {
        var events = new TopicExchange("mediflow.events", true, false);
        var dead = new TopicExchange("mediflow.events.dlx", true, false);
        var queue = QueueBuilder.durable("report.operational-v2.q")
                .deadLetterExchange(dead.getName()).deadLetterRoutingKey("report.operational-v2.dlq").build();
        var dlq = QueueBuilder.durable("report.operational-v2.dlq").build();
        return new Declarables(events, dead, queue, dlq,
                BindingBuilder.bind(queue).to(events).with("medicalrecord.completed"),
                BindingBuilder.bind(queue).to(events).with("lab.result.created"),
                BindingBuilder.bind(queue).to(events).with("prescription.filled"),
                BindingBuilder.bind(queue).to(events).with("surgery.completed"),
                BindingBuilder.bind(queue).to(events).with("surgery.cancelled"),
                BindingBuilder.bind(queue).to(events).with("admission.started"),
                BindingBuilder.bind(queue).to(events).with("admission.closed"),
                BindingBuilder.bind(dlq).to(dead).with(dlq.getName()));
    }

    @Bean
    ReceiveOperationalReportFactUseCase operationalReportFactReceiver(ApplyOperationalContributionUseCase operations,
            ProjectAdmissionReportEvidenceUseCase admissions, ZoneId reportZoneId) {
        return new OperationalReportFactReceiver(operations, admissions, reportZoneId);
    }

    @Bean
    OperationalReportFactConsumer operationalReportFactConsumer(CareFinanceWirePort decoder,
            ReceiveOperationalReportFactUseCase receiver) {
        return new OperationalReportFactConsumer(decoder, receiver);
    }

    @Bean
    SimpleRabbitListenerContainerFactory reportOperationalListenerFactory(ConnectionFactory connection,
            @Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connection);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setAutoStartup(autoStartup);
        factory.setDefaultRequeueRejected(false);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(2);
        factory.setPrefetchCount(10);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(new org.springframework.retry.policy.SimpleRetryPolicy(3,
                        Map.of(AmqpRejectAndDontRequeueException.class, false), true, true))
                .backOffOptions(100, 2, 500)
                .recoverer((message, error) -> {
                    throw new AmqpRejectAndDontRequeueException("Operational report retries exhausted");
                }).build());
        return factory;
    }
}
