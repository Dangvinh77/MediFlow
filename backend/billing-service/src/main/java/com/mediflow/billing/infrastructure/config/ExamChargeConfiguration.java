package com.mediflow.billing.infrastructure.config;

import com.mediflow.billing.application.port.in.IssueExamChargeUseCase;
import com.mediflow.billing.application.port.out.ExamChargeRepositoryPort;
import com.mediflow.billing.application.port.out.ExamChargeWirePort;
import com.mediflow.billing.application.port.out.PriceCatalogPort;
import com.mediflow.billing.application.service.ExamChargePlannedRequestService;
import com.mediflow.billing.messaging.consumer.ExamChargeConsumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Opt-in EXAM issuance. Both flags default false. Unlike {@link LabTestChargeConfiguration}, this
 * never declares a new {@code Binding}: {@value RabbitConfig#RK_APPOINTMENT_STATUS_CHANGED} is
 * already unconditionally bound to {@value RabbitConfig#QUEUE} for the legacy V0 compatibility
 * handler; enabling both flags only swaps {@code BillingEventConsumer}'s dispatch target for that
 * same routing key onto the strict V1 issuer below. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.exam-charge-consumer.enabled"}, havingValue = "true")
public class ExamChargeConfiguration {
    @Bean IssueExamChargeUseCase issueExamCharges(ExamChargeRepositoryPort repository, PriceCatalogPort prices,
            PlatformTransactionManager manager) {
        var service = new ExamChargePlannedRequestService(repository, prices);
        var transaction = new TransactionTemplate(manager); transaction.setTimeout(5);
        return command -> transaction.execute(status -> service.issue(command));
    }
    @Bean ExamChargeConsumer examChargeConsumer(ExamChargeWirePort wire, IssueExamChargeUseCase charges) {
        return new ExamChargeConsumer(wire, charges);
    }
}
