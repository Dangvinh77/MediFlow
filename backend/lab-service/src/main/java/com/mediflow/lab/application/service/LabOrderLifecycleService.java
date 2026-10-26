package com.mediflow.lab.application.service;

import com.mediflow.lab.application.dto.response.LabOrderLifecycleDTO;
import com.mediflow.lab.application.port.in.LookupLabOrderLifecycleUseCase;
import com.mediflow.lab.application.port.out.LabTestRepositoryPort;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LabOrderLifecycleService implements LookupLabOrderLifecycleUseCase {
    private final LabTestRepositoryPort tests;
    private final Clock clock;
    public LabOrderLifecycleService(LabTestRepositoryPort tests, Clock clock) { this.tests = tests; this.clock = clock; }
    @Override public LabOrderLifecycleDTO lookup(UUID sourceOrderId) {
        return tests.findBySourceOrderId(sourceOrderId).map(t -> new LabOrderLifecycleDTO(
                sourceOrderId, true, t.getTestId(), t.getRecordId(), t.getPatientId(), t.getRequestingDepartmentId(),
                t.getCareEpisodeType() == null ? null : t.getCareEpisodeType().name(), t.getCareEpisodeId(),
                t.getStatus().name(), clock.instant())).orElseGet(() -> new LabOrderLifecycleDTO(
                sourceOrderId, false, null, null, null, null, null, null, null, clock.instant()));
    }
}
