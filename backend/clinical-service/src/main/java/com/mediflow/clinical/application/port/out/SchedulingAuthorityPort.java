package com.mediflow.clinical.application.port.out;

import java.util.UUID;

import com.mediflow.clinical.application.dto.authority.ConsultationEligibilitySnapshot;
import com.mediflow.clinical.application.dto.authority.SchedulingAuthoritySnapshot;
import com.mediflow.clinical.application.dto.authority.CurrentDoctorAuthoritySnapshot;

public interface SchedulingAuthorityPort {
    SchedulingAuthoritySnapshot authority(UUID actorAccountId, UUID departmentId, String correlationId);

    ConsultationEligibilitySnapshot consultationEligibility(
            UUID doctorId, UUID departmentId, UUID roomId, String correlationId);

    CurrentDoctorAuthoritySnapshot currentDoctorAuthority(UUID actorAccountId, String correlationId);
}
