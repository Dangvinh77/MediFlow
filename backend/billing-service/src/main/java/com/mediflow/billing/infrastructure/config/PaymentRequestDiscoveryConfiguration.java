package com.mediflow.billing.infrastructure.config;

import com.mediflow.billing.application.port.in.DiscoverPaymentRequestUseCase;
import com.mediflow.billing.application.port.out.PaymentRequestDiscoveryPort;
import com.mediflow.billing.application.service.PaymentRequestDiscoveryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Opt-in L1 discovery. Both flags default false; disabled leaves the endpoint and its use case absent. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.payment-request-discovery.enabled"},
        havingValue = "true")
public class PaymentRequestDiscoveryConfiguration {
    @Bean
    DiscoverPaymentRequestUseCase discoverPaymentRequest(PaymentRequestDiscoveryPort discovery) {
        return new PaymentRequestDiscoveryService(discovery);
    }
}
