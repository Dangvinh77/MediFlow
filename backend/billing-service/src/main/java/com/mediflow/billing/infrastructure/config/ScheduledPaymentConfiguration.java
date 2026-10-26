package com.mediflow.billing.infrastructure.config;

import com.mediflow.billing.application.port.in.ConsumeOutpatientRegistrationFactUseCase;
import com.mediflow.billing.application.port.in.DiscoverRegistrationPaymentRequestUseCase;
import com.mediflow.billing.application.port.out.ClinicalPaymentContextPort;
import com.mediflow.billing.application.port.out.OutpatientRegistrationSourcePort;
import com.mediflow.billing.application.port.out.OutpatientQuoteCatalogPort;
import com.mediflow.billing.application.port.out.PriceCatalogPort;
import com.mediflow.billing.application.service.ConsumeOutpatientRegistrationFactService;
import com.mediflow.billing.application.service.DiscoverRegistrationPaymentRequestService;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.scheduled-payments.enabled"},
        havingValue = "true")
public class ScheduledPaymentConfiguration {
    @Bean
    com.mediflow.billing.application.port.in.FindScheduledPaymentQueueUseCase scheduledPaymentQueue(
            com.mediflow.billing.application.port.out.ScheduledPaymentQueuePort queue) {
        return new com.mediflow.billing.application.service.FindScheduledPaymentQueueService(queue);
    }

    public static final String REGISTRATION_QUEUE = "billing.scheduled-registration.q";

    @Bean
    Queue scheduledRegistrationQueue() {
        return QueueBuilder.durable(REGISTRATION_QUEUE).build();
    }

    @Bean
    Binding scheduledRegistrationBinding(Queue scheduledRegistrationQueue, TopicExchange mediflowEventsExchange) {
        return BindingBuilder.bind(scheduledRegistrationQueue).to(mediflowEventsExchange)
                .with("outpatient.registration.changed");
    }

    @Bean
    ConsumeOutpatientRegistrationFactUseCase consumeOutpatientRegistrationFacts(
            OutpatientRegistrationSourcePort sources, PriceCatalogPort prices,
            ObjectProvider<OutpatientQuoteCatalogPort> quotes,
            @Value("${mediflow.billing.outpatient-quotes.enabled:false}") boolean quotesEnabled) {
        return new ConsumeOutpatientRegistrationFactService(sources, prices, quotes.getIfAvailable(), quotesEnabled);
    }

    @Bean
    DiscoverRegistrationPaymentRequestUseCase discoverRegistrationPaymentRequest(
            OutpatientRegistrationSourcePort sources, ClinicalPaymentContextPort clinical) {
        return new DiscoverRegistrationPaymentRequestService(sources, clinical);
    }
}
