package com.mediflow.organization.application.port.in;

import com.mediflow.organization.application.dto.response.CareLabelDTO;
import java.util.Optional;
import java.util.UUID;

public interface GetCareLabelUseCase {
    Optional<CareLabelDTO> read(String kind, UUID id);
}
