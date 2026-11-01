package com.mediflow.organization.application.port.out;

import com.mediflow.organization.application.dto.response.DepartmentHeadSnapshot;
import com.mediflow.organization.application.dto.response.OwnCapabilities;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DepartmentHeadRepository {
    void lockCommands();

    boolean lockClinicalDepartment(UUID departmentId);

    Optional<UUID> lockEligibleDoctorAccount(UUID staffId, UUID departmentId);

    boolean hasActiveStaffAppointment(UUID staffId);

    DepartmentHeadSnapshot snapshot(UUID departmentId);

    Optional<Receipt> receipt(UUID actorId, UUID departmentId, String key);

    void replace(
            UUID actorId,
            UUID departmentId,
            UUID staffId,
            UUID accountId,
            UUID appointmentId,
            long revision,
            String reason,
            Instant now,
            String correlationId);

    void receipt(
            UUID actorId,
            UUID departmentId,
            String key,
            String command,
            DepartmentHeadSnapshot response);

    OwnCapabilities capabilities(UUID accountId, Instant observedAt);

    record Receipt(String command, DepartmentHeadSnapshot response) {}
}
