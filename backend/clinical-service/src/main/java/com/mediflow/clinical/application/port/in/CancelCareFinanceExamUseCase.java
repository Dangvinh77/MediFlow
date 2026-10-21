package com.mediflow.clinical.application.port.in;

import java.util.UUID;

import com.mediflow.clinical.application.dto.response.AppointmentDTO;

/** Cancels an outpatient V2 examination while recording the financial cancellation evidence. */
public interface CancelCareFinanceExamUseCase {
    AppointmentDTO cancelCareFinanceExam(UUID appointmentId, String reason);
}
