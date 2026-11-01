package com.mediflow.clinical.application.dto.response;
import java.time.*;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.mediflow.clinical.domain.model.DoctorWorkShift;
public record DoctorWorkShiftDTO(UUID shiftId, int revision, UUID doctorId, UUID departmentId,
        UUID roomId, LocalDate shiftDate, @JsonFormat(pattern="HH:mm") LocalTime startTime,
        @JsonFormat(pattern="HH:mm") LocalTime endTime, DoctorWorkShift.Status status) {}
