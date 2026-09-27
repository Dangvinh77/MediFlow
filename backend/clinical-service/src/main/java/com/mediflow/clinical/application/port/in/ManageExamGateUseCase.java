package com.mediflow.clinical.application.port.in;

import java.util.UUID;

import com.mediflow.clinical.application.dto.request.StartExamRequest;
import com.mediflow.clinical.application.dto.response.AppointmentDTO;

public interface ManageExamGateUseCase {
    AppointmentDTO checkIn(UUID appointmentId);
    AppointmentDTO startExam(UUID appointmentId, StartExamRequest request);
}
