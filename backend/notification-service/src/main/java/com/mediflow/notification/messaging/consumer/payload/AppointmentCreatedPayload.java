package com.mediflow.notification.messaging.consumer.payload;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Local wire projection khớp hợp đồng của clinical-service
 * (backend-spec/03-clinical.md §9: {@code {envelope, appointmentId, patientId, doctorId,
 * departmentId, appointmentDate, appointmentTime}}). {@code appointmentDate}/{@code appointmentTime}
 * đổ vào biến {@code ngayHen}/{@code gioHen} của {@code NotificationTemplates}.
 */
public record AppointmentCreatedPayload(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID appointmentId,
        UUID patientId,
        UUID doctorId,
        UUID departmentId,
        LocalDate appointmentDate,
        LocalTime appointmentTime
) {}
