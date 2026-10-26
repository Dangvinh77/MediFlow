package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.event.OutpatientRegistrationChangedEvent;
import com.mediflow.billing.domain.model.OutpatientQuoteLine;
import com.mediflow.billing.domain.model.RegistrationPaymentSourceSnapshot;
import com.mediflow.billing.application.port.out.PriceCatalogPort.PriceSnapshot;
import java.util.UUID;
import java.util.Optional;

public interface OutpatientRegistrationSourcePort {
    boolean requiresPrice(UUID registrationId);
    void apply(OutpatientRegistrationChangedEvent event, byte[] rawEnvelope,
               String payloadFingerprint, PriceSnapshot price, OutpatientQuoteLine quoteLine);
    Optional<RegistrationPaymentSourceSnapshot> findPaymentRequestByRegistration(UUID registrationId);
}
