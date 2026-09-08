package com.mediflow.clinical.application.port.out;

import com.mediflow.clinical.application.event.AppointmentCreatedEvent;
import com.mediflow.clinical.application.event.AppointmentStatusChangedEvent;
import com.mediflow.clinical.application.event.MedicalRecordCreatedEvent;
import com.mediflow.clinical.application.event.DiagnosisAddedEvent;

/** Adapter delivers after commit; application must not publish rolled-back state. */
public interface ClinicalEventPublisherPort {
    void publishAppointmentCreated(AppointmentCreatedEvent event);
    void publishAppointmentStatusChanged(AppointmentStatusChangedEvent event);
    void publishMedicalRecordCreated(MedicalRecordCreatedEvent event);
    void publishDiagnosisAdded(DiagnosisAddedEvent event);
}
