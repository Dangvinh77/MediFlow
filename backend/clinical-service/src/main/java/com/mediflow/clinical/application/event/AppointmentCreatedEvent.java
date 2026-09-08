package com.mediflow.clinical.application.event;

import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.mediflow.clinical.domain.model.Appointment;
/** Published after commit; notification uses the date and HH:mm time. */
public record AppointmentCreatedEvent(
        UUID eventId, Instant occurredAt, String correlationId,
        UUID appointmentId, UUID patientId, UUID doctorId, UUID departmentId,
        LocalDate appointmentDate, @JsonFormat(pattern = "HH:mm") LocalTime appointmentTime
) {
    public static final String ROUTING_KEY = "appointment.created";

    public static AppointmentCreatedEvent from(Appointment appointment, String correlationId) {
        return new AppointmentCreatedEvent(UUID.randomUUID(), Instant.now(), correlationId,
                appointment.getAppointmentId(), appointment.getPatientId(), appointment.getDoctorId(),
                appointment.getDepartmentId(), appointment.getAppointmentDate(), appointment.getAppointmentTime());
    }
}
