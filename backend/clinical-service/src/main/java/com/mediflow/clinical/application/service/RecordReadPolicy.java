package com.mediflow.clinical.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import com.mediflow.clinical.application.dto.authority.CurrentDoctorAuthoritySnapshot;
import com.mediflow.clinical.application.dto.authority.RecordReadDecision;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.port.out.AppointmentRepositoryPort;
import com.mediflow.clinical.application.port.out.RecordAccessDeclarationPort;
import com.mediflow.clinical.application.port.out.SchedulingAuthorityPort;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.AppointmentStatus;
import com.mediflow.clinical.domain.model.MedicalRecord;

/** The single place that decides who may read a medical record and on what basis. */
public class RecordReadPolicy {
    public static final String PURPOSE_REQUIRED = "RECORD_ACCESS_PURPOSE_REQUIRED";
    public static final String DENIED = "RECORD_ACCESS_DENIED";

    private static final Set<String> READER_ROLES = Set.of("DOCTOR", "ADMIN", "NURSE");
    private static final Set<AppointmentStatus> OPEN_STATUSES = Set.of(AppointmentStatus.PENDING,
            AppointmentStatus.ARRIVED, AppointmentStatus.AWAITING_PAYMENT, AppointmentStatus.READY_FOR_EXAM,
            AppointmentStatus.IN_EXAM);
    private static final Duration COMPLETED_WINDOW = Duration.ofDays(30);
    private static final String EMERGENCY = "EMERGENCY";

    private final AppointmentRepositoryPort appointments;
    private final RecordAccessDeclarationPort declarations;
    private final SchedulingAuthorityPort authority;

    public RecordReadPolicy(AppointmentRepositoryPort appointments, RecordAccessDeclarationPort declarations,
            SchedulingAuthorityPort authority) {
        this.appointments = appointments;
        this.declarations = declarations;
        this.authority = authority;
    }

    public RecordReadDecision forRecord(ActorIdentity actor, MedicalRecord record, Instant at, String correlationId) {
        return decide(actor, record.getPatientId(), record, at, correlationId);
    }

    public RecordReadDecision forPatient(ActorIdentity actor, UUID patientId, Instant at, String correlationId) {
        return decide(actor, patientId, null, at, correlationId);
    }

    private RecordReadDecision decide(ActorIdentity actor, UUID patientId, MedicalRecord record, Instant at,
            String correlationId) {
        if (actor == null || actor.accountId() == null || !READER_ROLES.contains(actor.role())) {
            return RecordReadDecision.deny(DENIED);
        }
        CurrentDoctorAuthoritySnapshot current = authority.currentDoctorAuthority(actor.accountId(), correlationId);
        if (!isCurrent(actor, current)) {
            return RecordReadDecision.deny(DENIED);
        }
        List<Appointment> valid = appointments.findByPatient(patientId).stream()
                .filter(appointment -> isValidBasis(appointment, at))
                .toList();
        return "NURSE".equals(actor.role())
                ? nurseDecision(actor, patientId, record, current.departmentId(), valid, at)
                : doctorOrAdminDecision(actor, patientId, record, current.departmentId(), valid, at);
    }

    private RecordReadDecision doctorOrAdminDecision(ActorIdentity actor, UUID patientId, MedicalRecord record,
            UUID currentDepartment, List<Appointment> valid, Instant at) {
        UUID staffId = actor.staffId();
        if (staffId != null && record != null && staffId.equals(record.getDoctorId())) {
            return RecordReadDecision.allow("AUTHOR");
        }
        if (staffId != null && anyMatch(valid, appointment -> staffId.equals(appointment.getDoctorId()))) {
            return RecordReadDecision.allow("CARE_ASSIGNMENT");
        }
        if ("DOCTOR".equals(actor.role())
                && anyMatch(valid, appointment -> currentDepartment.equals(appointment.getDepartmentId()))) {
            return RecordReadDecision.allow("DEPARTMENT_ENCOUNTER");
        }
        return declarations.findActive(actor.accountId(), patientId, at)
                .map(declaration -> RecordReadDecision.allow("DECLARED_PURPOSE"))
                .orElseGet(() -> RecordReadDecision.deny(PURPOSE_REQUIRED));
    }

    private RecordReadDecision nurseDecision(ActorIdentity actor, UUID patientId, MedicalRecord record,
            UUID currentDepartment, List<Appointment> valid, Instant at) {
        boolean departmentEncounter = anyMatch(valid,
                appointment -> currentDepartment.equals(appointment.getDepartmentId()));
        boolean ownDepartmentRecord = record == null || currentDepartment.equals(record.getDepartmentId());
        // A list has no single record, so a declared emergency must win or it could never widen the list.
        if (record != null && departmentEncounter && ownDepartmentRecord) {
            return RecordReadDecision.allowDepartment("DEPARTMENT_ENCOUNTER", currentDepartment);
        }
        boolean emergency = declarations.findActive(actor.accountId(), patientId, at)
                .filter(declaration -> EMERGENCY.equals(declaration.purpose()))
                .isPresent();
        if (emergency) {
            return RecordReadDecision.allow("DECLARED_PURPOSE");
        }
        if (record == null && departmentEncounter) {
            return RecordReadDecision.allowDepartment("DEPARTMENT_ENCOUNTER", currentDepartment);
        }
        return RecordReadDecision.deny(PURPOSE_REQUIRED);
    }

    private static boolean isCurrent(ActorIdentity actor, CurrentDoctorAuthoritySnapshot current) {
        if (!Boolean.TRUE.equals(current.accountExists()) || !Boolean.TRUE.equals(current.accountActive())
                || !actor.role().equals(current.currentRole()) || !Objects.equals(actor.staffId(), current.staffId())) {
            return false;
        }
        boolean adminWithoutStaff = "ADMIN".equals(actor.role()) && current.staffId() == null;
        return adminWithoutStaff || (Boolean.TRUE.equals(current.staffActive())
                && Boolean.TRUE.equals(current.departmentActive()) && current.departmentId() != null);
    }

    private static boolean isValidBasis(Appointment appointment, Instant at) {
        if (OPEN_STATUSES.contains(appointment.getStatus())) {
            return true;
        }
        return appointment.getStatus() == AppointmentStatus.COMPLETED && appointment.getCompletedAt() != null
                && !appointment.getCompletedAt().isBefore(at.minus(COMPLETED_WINDOW));
    }

    private static boolean anyMatch(List<Appointment> appointments, Predicate<Appointment> condition) {
        return appointments.stream().anyMatch(condition);
    }
}
