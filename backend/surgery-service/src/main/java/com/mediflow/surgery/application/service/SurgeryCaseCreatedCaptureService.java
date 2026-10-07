package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.mapper.SurgeryCareEventFactory;
import com.mediflow.surgery.application.port.in.CaptureSurgeryCaseCreatedUseCase;
import com.mediflow.surgery.application.port.out.SurgeryCareEventCapturePort;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.domain.exception.SurgeryCaseNotFoundException;
import com.mediflow.surgery.domain.model.SurgeryPlannedItem;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class SurgeryCaseCreatedCaptureService implements CaptureSurgeryCaseCreatedUseCase {
    private final SurgeryCaseRepositoryPort cases;
    private final SurgeryCareEventCapturePort events;
    public SurgeryCaseCreatedCaptureService(SurgeryCaseRepositoryPort cases, SurgeryCareEventCapturePort events) {
        this.cases = java.util.Objects.requireNonNull(cases);
        this.events = java.util.Objects.requireNonNull(events);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void capture(UUID surgeryCaseId, List<SurgeryPlannedItem> plannedItems) {
        java.util.Objects.requireNonNull(surgeryCaseId, "Case ID is required");
        var value = cases.lockById(surgeryCaseId).orElseThrow(() -> new SurgeryCaseNotFoundException(surgeryCaseId));
        events.hold(SurgeryCareEventFactory.created(value, plannedItems), value.getRevision());
    }
}
