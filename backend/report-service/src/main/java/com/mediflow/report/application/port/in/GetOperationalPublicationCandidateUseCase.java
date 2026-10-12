package com.mediflow.report.application.port.in;

import com.mediflow.report.application.dto.response.OperationalPublicationCandidateDTO;
import java.util.UUID;

public interface GetOperationalPublicationCandidateUseCase {
    OperationalPublicationCandidateDTO candidate(UUID generationId);
}
