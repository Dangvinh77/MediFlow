package com.mediflow.billing.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.port.in.CloseAdmissionChargesUseCase;
import com.mediflow.billing.application.port.out.AdmissionChargeCloseStorePort;
import com.mediflow.billing.application.service.AdmissionChargeCloseService;
import com.mediflow.billing.infrastructure.messaging.AdmissionChargeCloseDecoder;
import java.time.Clock;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.admission-charge-close.enabled"}, havingValue = "true")
public class AdmissionChargeCloseConfiguration {
    @Bean public com.mediflow.billing.application.port.out.AdmissionChargeCloseWirePort admissionChargeCloseDecoder(ObjectMapper mapper) {
        return new AdmissionChargeCloseDecoder(mapper);
    }

    @Bean public com.mediflow.billing.application.port.in.ConsumeAdmissionMedicalDischargeUseCase consumeAdmissionMedicalDischarge(
            com.mediflow.billing.application.port.out.AdmissionChargeCloseWirePort wire, CloseAdmissionChargesUseCase charges) {
        return (key, bytes) -> charges.close(wire.decode(key, bytes));
    }

    @Bean public CloseAdmissionChargesUseCase closeAdmissionCharges(AdmissionChargeCloseStorePort store, PlatformTransactionManager manager) {
        var service = new AdmissionChargeCloseService(store, Clock.systemUTC());
        var transaction = new TransactionTemplate(manager);
        transaction.setTimeout(5);
        return command -> transaction.executeWithoutResult(status -> service.close(command));
    }

    @Bean public Binding admissionMedicalDischargeBinding(Queue billingQueue, TopicExchange mediflowEventsExchange) {
        return BindingBuilder.bind(billingQueue).to(mediflowEventsExchange).with(AdmissionChargeCloseDecoder.ROUTING_KEY);
    }
}
