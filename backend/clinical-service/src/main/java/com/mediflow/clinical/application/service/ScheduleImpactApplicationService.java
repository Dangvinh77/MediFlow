package com.mediflow.clinical.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mediflow.clinical.application.dto.response.ScheduleImpactDTO;
import com.mediflow.clinical.application.port.out.ClinicQueueRepositoryPort;
import com.mediflow.clinical.application.port.out.ConsultationSessionRepositoryPort;
import com.mediflow.clinical.application.port.out.DoctorWorkShiftRepositoryPort;
import com.mediflow.clinical.application.port.out.OutpatientRegistrationRepositoryPort;
import com.mediflow.clinical.domain.model.ConsultationSession;
import com.mediflow.clinical.domain.model.OutpatientRegistrationStatus;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.exception.ResourceNotFoundException;

/** Who is affected before a manager stops or changes a schedule. */
@Service
@ConditionalOnClinicIntake
public class ScheduleImpactApplicationService {
    private static final Set<OutpatientRegistrationStatus> AFFECTED = Set.of(OutpatientRegistrationStatus.AWAITING_PAYMENT,
            OutpatientRegistrationStatus.CONFIRMED, OutpatientRegistrationStatus.IN_EXAM,
            OutpatientRegistrationStatus.RECONCILIATION_REQUIRED);
    private final ConsultationSessionRepositoryPort sessions;
    private final DoctorWorkShiftRepositoryPort shifts;
    private final OutpatientRegistrationRepositoryPort registrations;
    private final ConsultationSessionApplicationService schedules;
    private final ClinicQueueRepositoryPort tickets;

    public ScheduleImpactApplicationService(ConsultationSessionRepositoryPort sessions, DoctorWorkShiftRepositoryPort shifts,
            OutpatientRegistrationRepositoryPort registrations, ConsultationSessionApplicationService schedules,
            ClinicQueueRepositoryPort tickets) {
        this.tickets = tickets;
        this.sessions = sessions;
        this.shifts = shifts;
        this.registrations = registrations;
        this.schedules = schedules;
    }

    public ScheduleImpactDTO forSession(UUID sessionId) {
        ConsultationSession session = session(sessionId);
        requireScope(session.configuration().departmentId());
        return impact(List.of(session));
    }

    public ScheduleImpactDTO forShift(UUID shiftId) {
        var shift = shifts.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Shift was not found"));
        requireScope(shift.configuration().departmentId());
        return impact(shifts.linkedSessionIds(shiftId).stream().map(this::session).toList());
    }

    private void requireScope(UUID departmentId) {
        schedules.requireScheduleScope(schedules.requireHumanActor("ADMIN", "DOCTOR"), departmentId);
    }

    private ConsultationSession session(UUID id) {
        return sessions.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Session was not found"));
    }

    private ScheduleImpactDTO impact(List<ConsultationSession> affected) {
        int held = 0, locked = 0, confirmed = 0, consumed = 0;
        var rows = new ArrayList<ScheduleImpactDTO.AffectedRegistration>();
        for (ConsultationSession session : affected) {
            held += session.counts().heldCount();
            locked += session.counts().paymentLockedCount();
            confirmed += session.counts().confirmedCount();
            consumed += session.counts().consumedCount();
            rows.addAll(affectedRegistrations(session.sessionId()));
        }
        return new ScheduleImpactDTO(affected.stream().map(ConsultationSession::sessionId).toList(), held, locked, confirmed,
                consumed, rows);
    }

    private List<ScheduleImpactDTO.AffectedRegistration> affectedRegistrations(UUID sessionId) {
        var rows = new ArrayList<ScheduleImpactDTO.AffectedRegistration>();
        int page = 0;
        while (true) {
            var result = registrations.findBySession(sessionId, PageQuery.of(page, 100));
            var affected = result.content().stream().filter(r -> AFFECTED.contains(r.status())).toList();
            var rooms = tickets.activeNumbers(affected.stream().map(r -> r.registrationId()).toList());
            affected.forEach(r -> rows.add(new ScheduleImpactDTO.AffectedRegistration(r.registrationId(), r.patientId(), r.sessionId(),
                    r.status(), r.reservation().status(), r.checkedInAt(), r.queueNumber(), rooms.get(r.registrationId()))));
            page++;
            if (page * 100L >= result.totalElements() || result.content().isEmpty()) return rows;
        }
    }
}
