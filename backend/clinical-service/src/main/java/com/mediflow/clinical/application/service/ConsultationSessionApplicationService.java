package com.mediflow.clinical.application.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;
import java.util.HexFormat;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;

import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.authority.CurrentDoctorAuthoritySnapshot;
import com.mediflow.clinical.application.dto.request.ConsultationSessionCreateRequest;
import com.mediflow.clinical.application.dto.request.ConsultationSessionStatusRequest;
import com.mediflow.clinical.application.dto.request.ConsultationSessionUpdateRequest;
import com.mediflow.clinical.application.dto.response.ConsultationSessionDTO;
import com.mediflow.clinical.application.dto.response.ConsultationSessionDTO.BreakDTO;
import com.mediflow.clinical.application.dto.response.DoctorCalendar;
import com.mediflow.clinical.application.port.out.ConsultationSessionRepositoryPort;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.port.out.SchedulingAuthorityPort;
import com.mediflow.clinical.application.dto.authority.ConsultationEligibilitySnapshot;
import com.mediflow.clinical.application.dto.authority.SchedulingAuthoritySnapshot;
import com.mediflow.clinical.domain.model.ConsultationBreak;
import com.mediflow.clinical.domain.model.ConsultationSession;
import com.mediflow.clinical.domain.model.ConsultationSessionStatus;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.common.exception.ResourceNotFoundException;

@Service
@ConditionalOnProperty(prefix = "mediflow.clinical.scheduling", name = "enabled", havingValue = "true")
public class ConsultationSessionApplicationService {
    private static final String COLLECTION_PATH = "/api/v2/consultation-sessions";
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final ConsultationSessionRepositoryPort sessions;
    private final SchedulingAuthorityPort authority;
    private final CurrentClinicalActorPort actors;
    private final CorrelationIdProvider correlationIds;
    private final IdempotentCommandPort idempotency;
    private final Clock clock;

    public ConsultationSessionApplicationService(ConsultationSessionRepositoryPort sessions,
            SchedulingAuthorityPort authority, CurrentClinicalActorPort actors,
            CorrelationIdProvider correlationIds, IdempotentCommandPort idempotency,
            @Qualifier("clinicalSchedulingClock") Clock clinicalSchedulingClock) {
        this.sessions = sessions;
        this.authority = authority;
        this.actors = actors;
        this.correlationIds = correlationIds;
        this.idempotency = idempotency;
        this.clock = clinicalSchedulingClock;
    }

    public IdempotentCommandPort.Result<ConsultationSessionDTO> create(
            ConsultationSessionCreateRequest request, String idempotencyKey) {
        ActorIdentity actor = requireHumanActor("ADMIN", "MANAGER");
        requireScheduleScope(actor, request.departmentId());
        requireEligible(request.doctorId(), request.departmentId(), request.roomId());
        requireIdempotencyKey(idempotencyKey);
        return idempotency.execute(actor.accountId().toString(), "POST", COLLECTION_PATH, idempotencyKey,
                fingerprint(request), ConsultationSessionDTO.class, 201,
                data -> COLLECTION_PATH + "/" + data.sessionId(), () -> {
                    ConsultationSession session = ConsultationSession.open(UUID.randomUUID(), request.doctorId(),
                            request.departmentId(), request.roomId(), request.sessionDate(), request.startTime(),
                            request.endTime(), request.breaks().stream()
                                    .map(value -> new ConsultationBreak(value.startTime(), value.endTime())).toList(),
                            request.capacity());
                    return toDto(sessions.insert(session, actor.accountId(), null, clock.instant()));
                });
    }

    public IdempotentCommandPort.Result<ConsultationSessionDTO> update(
            UUID sessionId, ConsultationSessionUpdateRequest request, String idempotencyKey) {
        ConsultationSession visible = read(sessionId);
        ActorIdentity actor = requireHumanActor("ADMIN", "MANAGER");
        requireScheduleScope(actor, visible.configuration().departmentId());
        if (!visible.configuration().departmentId().equals(request.departmentId())) {
            throw new BusinessRuleException("SCHEDULE_INELIGIBLE", "A session cannot move to another department");
        }
        ConsultationSession.Configuration configuration = new ConsultationSession.Configuration(
                request.doctorId(), request.departmentId(), request.roomId(), request.sessionDate(),
                request.startTime(), request.endTime(), request.breaks().stream()
                        .map(value -> new ConsultationBreak(value.startTime(), value.endTime())).toList(),
                request.capacity());
        if (visible.status() == ConsultationSessionStatus.OPEN) {
            requireEligible(configuration.doctorId(), configuration.departmentId(), configuration.roomId());
        }
        requireIdempotencyKey(idempotencyKey);
        String path = COLLECTION_PATH + "/" + sessionId;
        return idempotency.execute(actor.accountId().toString(), "PUT", path, idempotencyKey,
                fingerprint(request), ConsultationSessionDTO.class, 200, ignored -> null, () -> {
                    ConsultationSession before = lock(sessionId);
                    if (!before.configuration().departmentId().equals(request.departmentId())) {
                        throw new BusinessRuleException("SCHEDULE_INELIGIBLE", "A session cannot move to another department");
                    }
                    ConsultationSession after = domain(() -> before.updateConfiguration(
                            request.expectedRevision(), configuration, request.reason()));
                    sessions.update(before, after, actor.accountId(), "UPDATED", request.reason(), clock.instant());
                    return toDto(after);
                });
    }

    public IdempotentCommandPort.Result<ConsultationSessionDTO> changeStatus(
            UUID sessionId, ConsultationSessionStatusRequest request, String idempotencyKey) {
        ConsultationSession visible = read(sessionId);
        ActorIdentity actor = requireHumanActor("ADMIN", "MANAGER");
        requireScheduleScope(actor, visible.configuration().departmentId());
        if (request.status() == ConsultationSessionStatus.OPEN) {
            var config = visible.configuration();
            requireEligible(config.doctorId(), config.departmentId(), config.roomId());
        }
        requireIdempotencyKey(idempotencyKey);
        String path = COLLECTION_PATH + "/" + sessionId + "/status";
        return idempotency.execute(actor.accountId().toString(), "PUT", path, idempotencyKey,
                fingerprint(request), ConsultationSessionDTO.class, 200, ignored -> null, () -> {
                    ConsultationSession before = lock(sessionId);
                    ConsultationSession after = domain(() -> before.changeStatus(
                            request.expectedRevision(), request.status(), request.reason()));
                    sessions.update(before, after, actor.accountId(), "STATUS_CHANGED", request.reason(), clock.instant());
                    return toDto(after);
                });
    }

    public PageResult<ConsultationSessionDTO> search(UUID departmentId, LocalDate fromDate, LocalDate toDate,
            PageQuery page) {
        ActorIdentity actor = requireHumanActor("ADMIN", "MANAGER", "NURSE");
        if (departmentId == null || fromDate == null || toDate == null || fromDate.isAfter(toDate)
                || ChronoUnit.DAYS.between(fromDate, toDate) > 30) {
            throw new BusinessRuleException("INVALID_REQUEST", "A valid session date range of at most 31 days is required");
        }
        if ("MANAGER".equals(actor.role())) requireScheduleScope(actor, departmentId);
        return sessions.search(departmentId, fromDate, toDate, page).map(this::toDto);
    }

    public DoctorCalendar mine(LocalDate weekStart, LocalDate date) {
        if ((weekStart == null) == (date == null)
                || weekStart != null && weekStart.getDayOfWeek() != java.time.DayOfWeek.MONDAY) {
            throw new BusinessRuleException("INVALID_REQUEST",
                    "Provide exactly one of date or Monday weekStart");
        }
        LocalDate fromDate = date == null ? weekStart : date;
        LocalDate toDate = date == null ? weekStart.plusDays(6) : date;
        ActorIdentity actor = requireHumanActor("DOCTOR");
        if (actor.staffId() == null) {
            throw new ForbiddenOperationException("SCHEDULING_SCOPE_DENIED", "A verified doctor staff identity is required");
        }
        CurrentDoctorAuthoritySnapshot snapshot = authority.currentDoctorAuthority(actor.accountId(),
                correlationIds.currentOrCreate().toString());
        if (!Boolean.TRUE.equals(snapshot.accountExists()) || !Boolean.TRUE.equals(snapshot.accountActive())
                || !"DOCTOR".equals(snapshot.currentRole()) || !actor.staffId().equals(snapshot.staffId())
                || !Boolean.TRUE.equals(snapshot.staffActive()) || snapshot.departmentId() == null
                || !Boolean.TRUE.equals(snapshot.departmentActive())) {
            throw new ForbiddenOperationException("SCHEDULING_SCOPE_DENIED", "Doctor has no current active schedule authority");
        }
        List<ConsultationSessionDTO> calendar = sessions.findByDoctor(actor.staffId(), fromDate, toDate)
                .stream().map(this::toDto).toList();
        return new DoctorCalendar(actor.staffId(), fromDate, toDate, CLINIC_ZONE.getId(), calendar);
    }

    private ConsultationSession lock(UUID sessionId) {
        if (sessionId == null) throw new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Session was not found");
        return sessions.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Session was not found"));
    }

    private ConsultationSession read(UUID sessionId) {
        if (sessionId == null) throw new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Session was not found");
        return sessions.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Session was not found"));
    }

    private ActorIdentity requireHumanActor(String... roles) {
        ActorIdentity actor = actors.current();
        if (actor == null || actor.accountId() == null || actor.role() == null) {
            throw new ForbiddenOperationException("SCHEDULING_SCOPE_DENIED", "A verified clinical actor is required");
        }
        for (String role : roles) if (role.equals(actor.role())) return actor;
        throw new ForbiddenOperationException("SCHEDULING_SCOPE_DENIED", "Actor role cannot use this scheduling command");
    }

    private void requireScheduleScope(ActorIdentity actor, UUID departmentId) {
        String correlationId = correlationIds.currentOrCreate().toString();
        SchedulingAuthoritySnapshot snapshot = authority.authority(actor.accountId(), departmentId, correlationId);
        if (!actor.role().equals(snapshot.currentRole()) || !Boolean.TRUE.equals(snapshot.accountExists())
                || !Boolean.TRUE.equals(snapshot.accountActive()) || !Boolean.TRUE.equals(snapshot.departmentActive())) {
            throw new ForbiddenOperationException("SCHEDULING_SCOPE_DENIED", "Actor has no current scheduling scope");
        }
        if ("MANAGER".equals(actor.role()) && (!Boolean.TRUE.equals(snapshot.staffActive())
                || actor.staffId() == null || !actor.staffId().equals(snapshot.staffId())
                || !Boolean.TRUE.equals(snapshot.canManageSchedules()))) {
            throw new ForbiddenOperationException("SCHEDULING_SCOPE_DENIED", "Manager is not assigned to this department");
        }
        if ("ADMIN".equals(actor.role()) && !Boolean.TRUE.equals(snapshot.canManageSchedules())) {
            throw new ForbiddenOperationException("SCHEDULING_SCOPE_DENIED", "Administrator has no scheduling authority");
        }
    }

    private void requireEligible(UUID doctorId, UUID departmentId, UUID roomId) {
        ConsultationEligibilitySnapshot snapshot = authority.consultationEligibility(
                doctorId, departmentId, roomId, correlationIds.currentOrCreate().toString());
        if (!Boolean.TRUE.equals(snapshot.eligible()) || !Boolean.TRUE.equals(snapshot.doctorActive())
                || !Boolean.TRUE.equals(snapshot.eligibleDoctor()) || !Boolean.TRUE.equals(snapshot.departmentActive())
                || !Boolean.TRUE.equals(snapshot.roomActive()) || !"CONSULTATION".equals(snapshot.roomType())) {
            throw new BusinessRuleException("SCHEDULE_INELIGIBLE", "Doctor, department, or room is not currently eligible");
        }
    }

    private static ConsultationSession domain(java.util.function.Supplier<ConsultationSession> operation) {
        try {
            return operation.get();
        } catch (IllegalStateException conflict) {
            String code = conflict.getMessage() == null ? "INVALID_REQUEST" : conflict.getMessage();
            if ("REVISION_CONFLICT".equals(code) || "SESSION_HAS_REGISTRATIONS".equals(code)) {
                throw new DuplicateResourceException(code, code);
            }
            throw new BusinessRuleException(code, code);
        } catch (IllegalArgumentException invalid) {
            throw new BusinessRuleException("INVALID_REQUEST", invalid.getMessage());
        }
    }

    private static void requireIdempotencyKey(String key) {
        if (key == null || key.isEmpty() || key.length() > 120
                || !key.chars().allMatch(value -> value >= 33 && value <= 126)) {
            throw new BusinessRuleException("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be 1-120 printable ASCII characters without spaces");
        }
    }

    private static String fingerprint(Object command) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(command.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private ConsultationSessionDTO toDto(ConsultationSession session) {
        var config = session.configuration();
        return new ConsultationSessionDTO(session.sessionId(), session.revision(), config.doctorId(),
                config.departmentId(), config.roomId(), config.sessionDate(), config.startTime(), config.endTime(),
                config.breaks().stream().map(value -> new BreakDTO(value.startTime(), value.endTime())).toList(),
                config.capacity(), session.status(), session.counts().heldCount(),
                session.counts().paymentLockedCount(), session.counts().confirmedCount(), session.counts().consumedCount(),
                session.counts().availableCount(), clock.instant());
    }

}
