package com.mediflow.billing.application.port.in;

import com.mediflow.billing.application.dto.response.RegistrationPaymentRequestDTO;
import java.util.UUID;

public interface DiscoverRegistrationPaymentRequestUseCase {
    RegistrationPaymentRequestDTO discover(UUID registrationId);
}
