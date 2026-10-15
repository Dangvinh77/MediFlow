package com.mediflow.lab.application.port.in;

import com.mediflow.lab.application.dto.request.CreateLabRequest;
import com.mediflow.lab.application.dto.response.LabTestDTO;

/** Transactional local creation boundary for Lab requests. */
public interface CreateLabRequestUseCase {
    LabTestDTO createCompatibility(CreateLabRequest request);
    LabTestDTO createCareFinance(CreateLabRequest request, String correlationId);
}
