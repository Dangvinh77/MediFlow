package com.mediflow.billing.application.port.out;

import java.util.Optional;
import com.mediflow.billing.application.dto.command.PaymentRequestSourceQuery;
import com.mediflow.billing.application.dto.response.PaymentRequestDiscoveryDTO;

public interface PaymentRequestDiscoveryPort {
    Optional<PaymentRequestDiscoveryDTO> findExact(PaymentRequestSourceQuery query);
}
