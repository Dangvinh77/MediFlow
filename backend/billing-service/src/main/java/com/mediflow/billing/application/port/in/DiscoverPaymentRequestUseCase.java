package com.mediflow.billing.application.port.in;

import com.mediflow.billing.application.dto.command.PaymentRequestSourceQuery;
import com.mediflow.billing.application.dto.response.PaymentRequestDiscoveryDTO;

public interface DiscoverPaymentRequestUseCase {
    PaymentRequestDiscoveryDTO discover(PaymentRequestSourceQuery query);
}
