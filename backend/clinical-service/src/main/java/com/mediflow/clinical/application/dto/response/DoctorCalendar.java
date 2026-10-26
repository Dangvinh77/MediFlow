package com.mediflow.clinical.application.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DoctorCalendar(UUID doctorId, LocalDate fromDate, LocalDate toDate, String timezone,
        List<ConsultationSessionDTO> sessions) {
    public DoctorCalendar {
        sessions = List.copyOf(sessions);
    }
}
