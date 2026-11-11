package com.mediflow.clinical.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.clinical.application.dto.authority.SchedulingAuthoritySnapshot;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.request.AddCourseVisitRequest;
import com.mediflow.clinical.application.dto.request.CloseTreatmentCourseRequest;
import com.mediflow.clinical.application.dto.request.OpenTreatmentCourseRequest;
import com.mediflow.clinical.application.dto.response.TreatmentCourseDTO;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.RecordAccessPolicyMode;
import com.mediflow.clinical.application.port.out.SchedulingAuthorityPort;
import com.mediflow.clinical.application.port.out.StaffLookupPort;
import com.mediflow.clinical.application.port.out.TreatmentCourseRepositoryPort;
import com.mediflow.clinical.application.port.out.TreatmentCourseRepositoryPort.CourseView;
import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import com.mediflow.clinical.domain.exception.MedicalRecordNotFoundException;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.TreatmentCourse;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.common.exception.ResourceNotFoundException;

/**
 * Treatment courses group visits a doctor chooses. They publish no event: Report and Pharmacy keep counting and
 * prescribing per visit.
 */
@Service
@ConditionalOnProperty(prefix = "mediflow.v2", name = "treatment-course", havingValue = "true")
public class TreatmentCourseApplicationService {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final TreatmentCourseRepositoryPort courses;
    private final MedicalRecordRepositoryPort records;
    private final CurrentClinicalActorPort actors;
    private final ClinicalStaffAuthority staffAuthority;
    private final SchedulingAuthorityPort authority;
    private final RecordReadGuard readGuard;
    private final boolean recordAccessPolicyEnabled;
    private final IdempotentCommandPort idempotency;
    private final Clock clock;
    private final CorrelationIdProvider correlationIds;

    @Autowired
    public TreatmentCourseApplicationService(TreatmentCourseRepositoryPort courses, MedicalRecordRepositoryPort records,
            CurrentClinicalActorPort actors, StaffLookupPort staff, SchedulingAuthorityPort authority,
            RecordReadGuard readGuard, RecordAccessPolicyMode mode, IdempotentCommandPort idempotency,
            CorrelationIdProvider correlationIds) {
        this(courses, records, actors, staff, authority, readGuard, mode.enabled(), idempotency, Clock.system(CLINIC_ZONE),
                correlationIds);
    }

    public TreatmentCourseApplicationService(TreatmentCourseRepositoryPort courses, MedicalRecordRepositoryPort records,
            CurrentClinicalActorPort actors, StaffLookupPort staff, SchedulingAuthorityPort authority,
            RecordReadGuard readGuard, boolean recordAccessPolicyEnabled, IdempotentCommandPort idempotency, Clock clock,
            CorrelationIdProvider correlationIds) {
        this.courses = courses;
        this.records = records;
        this.actors = actors;
        this.staffAuthority = new ClinicalStaffAuthority(actors, staff);
        this.authority = authority;
        this.readGuard = readGuard;
        this.recordAccessPolicyEnabled = recordAccessPolicyEnabled;
        this.idempotency = idempotency;
        this.clock = clock;
        this.correlationIds = correlationIds;
    }

    public IdempotentCommandPort.Result<TreatmentCourseDTO> open(OpenTreatmentCourseRequest request, String key) {
        ActorIdentity actor = doctor();
        requireKey(key);
        return idempotency.execute(actor.accountId().toString(), "POST", "/api/v2/treatment-courses", key,
                fingerprint(String.valueOf(request)), TreatmentCourseDTO.class, 201,
                dto -> dto == null ? null : "/api/v2/treatment-courses/" + dto.courseId(),
                () -> openNow(actor, request));
    }

    public IdempotentCommandPort.Result<TreatmentCourseDTO> addVisit(UUID courseId, AddCourseVisitRequest request,
            String key) {
        ActorIdentity actor = doctor();
        requireKey(key);
        String path = "/api/v2/treatment-courses/" + courseId + "/visits";
        return idempotency.execute(actor.accountId().toString(), "POST", path, key, fingerprint(path + ":" + request),
                TreatmentCourseDTO.class, 200, ignored -> null, () -> addVisitNow(actor, courseId, request));
    }

    public IdempotentCommandPort.Result<TreatmentCourseDTO> close(UUID courseId, CloseTreatmentCourseRequest request,
            String key) {
        ActorIdentity actor = doctor();
        requireKey(key);
        String path = "/api/v2/treatment-courses/" + courseId + "/closure";
        return idempotency.execute(actor.accountId().toString(), "PUT", path, key, fingerprint(path + ":" + request),
                TreatmentCourseDTO.class, 200, ignored -> null, () -> closeNow(actor, courseId, request));
    }

    @Transactional(readOnly = true)
    public List<TreatmentCourseDTO> forPatient(UUID patientId) {
        UUID nurseDepartmentId = recordAccessPolicyEnabled ? readGuard.requirePatient(patientId).nurseDepartmentId() : null;
        LocalDate today = LocalDate.now(clock.withZone(CLINIC_ZONE));
        return courses.findByPatient(patientId).stream()
                .filter(view -> nurseDepartmentId == null || nurseDepartmentId.equals(view.course().getDepartmentId()))
                .map(view -> dto(view, today))
                .toList();
    }

    TreatmentCourseDTO openNow(ActorIdentity actor, OpenTreatmentCourseRequest request) {
        MedicalRecord first = record(request.recordId());
        if (!first.getPatientId().equals(request.patientId())) {
            throw new InvalidClinicalDataException("TREATMENT_COURSE_INVALID", "The first visit belongs to another patient");
        }
        staffAuthority.requireAssignedDoctor(first.getDoctorId(), first.getDepartmentId());
        TreatmentCourse course = TreatmentCourse.open(request.patientId(), first.getDepartmentId(), request.problem(),
                request.openingReason(), actor.staffId(), first.getRecordId(), clock.instant());
        courses.insert(course);
        return current(course.getCourseId());
    }

    TreatmentCourseDTO addVisitNow(ActorIdentity actor, UUID courseId, AddCourseVisitRequest request) {
        TreatmentCourse course = locked(courseId);
        MedicalRecord visit = record(request.recordId());
        if (!visit.getPatientId().equals(course.getPatientId())) {
            throw new InvalidClinicalDataException("TREATMENT_COURSE_INVALID", "The visit belongs to another patient");
        }
        if (recordAccessPolicyEnabled) {
            readGuard.requireRecord(visit);
        }
        Instant at = clock.instant();
        if (course.addVisit(visit.getRecordId(), actor.staffId(), at)) {
            courses.addVisit(courseId, visit.getRecordId(), actor.staffId(), at);
        }
        return current(courseId);
    }

    TreatmentCourseDTO closeNow(ActorIdentity actor, UUID courseId, CloseTreatmentCourseRequest request) {
        TreatmentCourse course = locked(courseId);
        if (!actor.staffId().equals(course.getResponsibleDoctorId()) && !isHead(actor, course.getDepartmentId())) {
            throw new ForbiddenOperationException("CLINICAL_DOCTOR_NOT_ASSIGNED",
                    "Only the course doctor or the department head may close a treatment course");
        }
        course.close(request.decision(), request.note(), actor.staffId(), clock.instant());
        courses.close(course);
        return current(courseId);
    }

    private boolean isHead(ActorIdentity actor, UUID departmentId) {
        SchedulingAuthoritySnapshot snapshot = authority.authority(actor.accountId(), departmentId,
                correlationIds.currentOrCreate().toString());
        return snapshot != null && Boolean.TRUE.equals(snapshot.canManageSchedules())
                && Boolean.TRUE.equals(snapshot.staffActive()) && actor.staffId().equals(snapshot.staffId());
    }

    private TreatmentCourse locked(UUID courseId) {
        return courses.findByIdForUpdate(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("TREATMENT_COURSE_NOT_FOUND", "Treatment course not found: " + courseId));
    }

    private MedicalRecord record(UUID recordId) {
        return records.findById(recordId)
                .orElseThrow(() -> new MedicalRecordNotFoundException("Medical record not found: " + recordId));
    }

    private TreatmentCourseDTO current(UUID courseId) {
        LocalDate today = LocalDate.now(clock.withZone(CLINIC_ZONE));
        return courses.view(courseId).map(view -> dto(view, today)).orElse(null);
    }

    static TreatmentCourseDTO dto(CourseView view, LocalDate today) {
        TreatmentCourse course = view.course();
        boolean open = course.getStatus() == com.mediflow.clinical.domain.model.TreatmentCourseStatus.OPEN;
        return new TreatmentCourseDTO(course.getCourseId(), course.getPatientId(), course.getDepartmentId(),
                course.getProblem(), course.getOpeningReason(), course.getResponsibleDoctorId(), course.getStatus().name(),
                course.getRecordIds(), course.getOpenedAt(), view.lastVisitDate(),
                open && TreatmentCourse.reviewDue(today, view.lastVisitDate()),
                course.getClosureDecision() == null ? null : course.getClosureDecision().name(), course.getClosureNote(),
                course.getClosedAt());
    }

    private ActorIdentity doctor() {
        ActorIdentity actor = actors.current();
        if (actor == null || actor.accountId() == null || actor.staffId() == null || !"DOCTOR".equals(actor.role())) {
            throw new ForbiddenOperationException("CLINICAL_STAFF_IDENTITY_REQUIRED", "A doctor identity is required");
        }
        return actor;
    }

    static void requireKey(String key) {
        if (key == null || key.isEmpty() || key.length() > 120 || !key.chars().allMatch(value -> value >= 33 && value <= 126)) {
            throw new BusinessRuleException("INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must be 1-120 printable ASCII characters without spaces");
        }
    }

    static String fingerprint(String command) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(command.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
