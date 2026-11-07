package com.mediflow.billing.infrastructure.config;

import com.mediflow.billing.application.port.in.DiscoverPaymentRequestUseCase;
import com.mediflow.billing.application.port.in.FindPaymentHistoryUseCase;
import com.mediflow.billing.application.port.in.FindPaymentRequestQueueUseCase;
import com.mediflow.billing.application.port.out.PaymentHistoryPort;
import com.mediflow.billing.application.port.out.PaymentRequestDiscoveryPort;
import com.mediflow.billing.application.port.out.PaymentRequestQueuePort;
import com.mediflow.billing.application.service.PaymentHistoryService;
import com.mediflow.billing.application.service.PaymentRequestDiscoveryService;
import com.mediflow.billing.application.service.PaymentRequestQueueService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Opt-in L1 discovery. Both flags default false; disabled leaves the endpoint and its use case absent. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.payment-request-discovery.enabled"},
        havingValue = "true")
public class PaymentRequestDiscoveryConfiguration {
    @Bean
    FindPaymentRequestQueueUseCase paymentRequestQueue(PaymentRequestQueuePort queue) {
        return new PaymentRequestQueueService(queue);
    }

    @Bean
    FindPaymentHistoryUseCase paymentHistory(PaymentHistoryPort history) {
        return new PaymentHistoryService(history);
    }

    @Bean
    DiscoverPaymentRequestUseCase discoverPaymentRequest(PaymentRequestDiscoveryPort discovery) {
        return new PaymentRequestDiscoveryService(discovery);
    }
}
