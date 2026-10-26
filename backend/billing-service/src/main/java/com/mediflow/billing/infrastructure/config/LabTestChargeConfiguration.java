package com.mediflow.billing.infrastructure.config;

import com.mediflow.billing.application.port.in.IssueLabTestChargeUseCase;
import com.mediflow.billing.application.port.out.LabTestChargeRepositoryPort;
import com.mediflow.billing.application.port.out.LabTestChargeWirePort;
import com.mediflow.billing.application.port.out.PriceCatalogPort;
import com.mediflow.billing.application.port.out.OutpatientQuoteCatalogPort;
import com.mediflow.billing.application.service.LabTestPlannedRequestService;
import com.mediflow.billing.application.service.IssueOutpatientQuoteService;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import com.mediflow.billing.messaging.consumer.LabTestChargeConsumer;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Opt-in LAB_TEST issuance. Both flags default false; disabled leaves {@code lab.request.created}
 * unbound on {@value RabbitConfig#QUEUE}, same as before this slice existed. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.lab-test-charge-consumer.enabled"}, havingValue = "true")
public class LabTestChargeConfiguration {
    @Bean Binding labRequestCreatedBinding(Queue billingQueue, TopicExchange mediflowEventsExchange) {
        return BindingBuilder.bind(billingQueue).to(mediflowEventsExchange).with(RabbitConfig.RK_LAB_REQUEST_CREATED);
    }
    @Bean IssueLabTestChargeUseCase issueLabTestCharges(LabTestChargeRepositoryPort repository, PriceCatalogPort prices,
            com.mediflow.billing.application.port.out.OutpatientCancellationRepositoryPort cancellations,
            ObjectProvider<OutpatientQuoteCatalogPort> quoteCatalog,
            ObjectProvider<IssueOutpatientQuoteService> quoteIssuer,
            @Value("${mediflow.billing.outpatient-quotes.enabled:false}") boolean quotesEnabled,
            PlatformTransactionManager manager) {
        var service = new LabTestPlannedRequestService(repository, prices, cancellations,
                quoteCatalog.getIfAvailable(), quoteIssuer.getIfAvailable(), quotesEnabled, Clock.systemUTC());
        var transaction = new TransactionTemplate(manager); transaction.setTimeout(5);
        return command -> transaction.execute(status -> service.issue(command));
    }
    @Bean LabTestChargeConsumer labTestChargeConsumer(LabTestChargeWirePort wire, IssueLabTestChargeUseCase charges) {
        return new LabTestChargeConsumer(wire, charges);
    }
}
