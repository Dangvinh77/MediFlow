package com.mediflow.billing.application.port.in;

import com.mediflow.billing.application.dto.response.SurgeryCancellationDTO;
import java.util.UUID;

public interface GetSurgeryCancellationUseCase {
    SurgeryCancellationDTO get(UUID surgeryCaseId);
}
