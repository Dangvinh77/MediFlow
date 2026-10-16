package com.mediflow.billing.application.service;

import com.mediflow.billing.application.dto.command.PaymentRequestSourceQuery;
import com.mediflow.billing.application.dto.response.PaymentRequestDiscoveryDTO;
import com.mediflow.billing.application.port.in.DiscoverPaymentRequestUseCase;
import com.mediflow.billing.application.port.out.PaymentRequestDiscoveryPort;
import com.mediflow.billing.domain.exception.LedgerPaymentRequestNotFoundException;

/** Read-only snapshot lookup; never issues a charge/request and has no side effect. */
public final class PaymentRequestDiscoveryService implements DiscoverPaymentRequestUseCase {
    private final PaymentRequestDiscoveryPort discovery;

    public PaymentRequestDiscoveryService(PaymentRequestDiscoveryPort discovery) {
        this.discovery = discovery;
    }

    @Override
    public PaymentRequestDiscoveryDTO discover(PaymentRequestSourceQuery query) {
        return discovery.findExact(query).orElseThrow(() -> new LedgerPaymentRequestNotFoundException(
                "Payment request not found for " + query.purpose() + " source " + query.sourceId()));
    }
}
