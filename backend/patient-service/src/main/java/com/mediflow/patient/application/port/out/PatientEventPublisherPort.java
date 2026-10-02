package com.mediflow.patient.application.port.out;

import com.mediflow.patient.application.event.PatientCreatedEvent;
import com.mediflow.patient.application.event.PatientUpdatedEvent;

public interface PatientEventPublisherPort {
    void publishCreated(PatientCreatedEvent event);

    void publishUpdated(PatientUpdatedEvent event);
}
