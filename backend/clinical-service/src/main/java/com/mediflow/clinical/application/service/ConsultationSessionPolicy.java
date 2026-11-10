package com.mediflow.clinical.application.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.dto.request.ConsultationSessionCreateRequest;
import com.mediflow.clinical.application.port.out.DoctorWorkShiftRepositoryPort;
import com.mediflow.clinical.domain.model.ConsultationBreak;
import com.mediflow.clinical.domain.model.ConsultationSession;
import com.mediflow.clinical.domain.model.DoctorWorkShift;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;

/** The one rule set for sessions, whether created from a work shift or on their own. */
@Component
@ConditionalOnProperty(prefix = "mediflow.clinical.scheduling", name = "enabled", havingValue = "true")
public class ConsultationSessionPolicy {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final DoctorWorkShiftRepositoryPort shifts;
    private final ClinicIntakeSettings settings;
    private final Clock clock;

    public ConsultationSessionPolicy(DoctorWorkShiftRepositoryPort shifts, ClinicIntakeSettings settings,
            @Qualifier("clinicalSchedulingClock") Clock clock) {
        this.shifts = shifts;
        this.settings = settings;
        this.clock = clock;
    }

    /** A session that may be created; a source shift must contain it. */
    public ConsultationSession prepareNew(ConsultationSessionCreateRequest request, Optional<DoctorWorkShift> sourceShift) {
        ConsultationSession session = ConsultationSession.open(UUID.randomUUID(), request.doctorId(), request.departmentId(),
                request.roomId(), request.sessionDate(), request.startTime(), request.endTime(),
                request.breaks().stream().map(b -> new ConsultationBreak(b.startTime(), b.endTime())).toList(),
                request.capacity());
        sourceShift.ifPresent(shift -> shift.requireContains(session.configuration()));
        if (settings.enabled()) {
            LocalDate today = LocalDate.now(clock.withZone(CLINIC_ZONE));
            if (request.sessionDate().isBefore(today)) {
                throw new BusinessRuleException("SESSION_DATE_PAST", "A session cannot be created for a past date");
            }
            if (sourceShift.isEmpty()) requireNoConflictingShift(session.configuration());
        }
        return session;
    }

    public void requireUpdateAllowed(UUID sessionId, ConsultationSession.Configuration replacement) {
        if (!settings.enabled()) return;
        Optional<DoctorWorkShift> source = shifts.findBySession(sessionId);
        if (source.isEmpty()) {
            requireNoConflictingShift(replacement);
            return;
        }
        try {
            source.get().requireContains(replacement);
        } catch (IllegalStateException mismatch) {
            throw new DuplicateResourceException("SHIFT_SESSION_MISMATCH", "A linked session must stay inside its work shift");
        }
    }

    public void requireReopenAllowed(UUID sessionId) {
        if (!settings.enabled()) return;
        if (shifts.findBySession(sessionId).filter(shift -> shift.status() != DoctorWorkShift.Status.ACTIVE).isPresent()) {
            throw new DuplicateResourceException("SHIFT_CANCELLED", "The work shift of this session has been cancelled");
        }
    }

    private void requireNoConflictingShift(ConsultationSession.Configuration session) {
        boolean conflict = shifts.findByDoctor(session.doctorId(), session.sessionDate(), session.sessionDate()).stream()
                .filter(shift -> shift.status() == DoctorWorkShift.Status.ACTIVE)
                .filter(shift -> !shift.configuration().roomId().equals(session.roomId()))
                .anyMatch(shift -> session.startTime().isBefore(shift.configuration().endTime())
                        && session.endTime().isAfter(shift.configuration().startTime()));
        if (conflict) {
            throw new BusinessRuleException("SESSION_SHIFT_CONFLICT", "The doctor has an active work shift in another room at this time");
        }
    }
}
