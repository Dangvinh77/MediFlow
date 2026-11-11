package com.mediflow.clinical.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;

/** Visits a doctor groups under one problem. A visit may belong to several courses; content stays in the visit. */
public final class TreatmentCourse {
    public static final int REVIEW_AFTER_DAYS = 180;

    private final UUID courseId;
    private final UUID patientId;
    private final UUID departmentId;
    private final String problem;
    private final String openingReason;
    private final UUID responsibleDoctorId;
    private TreatmentCourseStatus status;
    private final List<UUID> recordIds;
    private final Instant openedAt;
    private TreatmentCourseDecision closureDecision;
    private String closureNote;
    private UUID closedByStaffId;
    private Instant closedAt;

    private TreatmentCourse(UUID courseId, UUID patientId, UUID departmentId, String problem, String openingReason,
            UUID responsibleDoctorId, TreatmentCourseStatus status, List<UUID> recordIds, Instant openedAt,
            TreatmentCourseDecision closureDecision, String closureNote, UUID closedByStaffId, Instant closedAt) {
        this.courseId = courseId;
        this.patientId = patientId;
        this.departmentId = departmentId;
        this.problem = problem;
        this.openingReason = openingReason;
        this.responsibleDoctorId = responsibleDoctorId;
        this.status = status;
        this.recordIds = new ArrayList<>(recordIds);
        this.openedAt = openedAt;
        this.closureDecision = closureDecision;
        this.closureNote = closureNote;
        this.closedByStaffId = closedByStaffId;
        this.closedAt = closedAt;
    }

    public static TreatmentCourse open(UUID patientId, UUID departmentId, String problem, String openingReason,
            UUID doctorId, UUID firstRecordId, Instant at) {
        if (patientId == null || departmentId == null || doctorId == null || firstRecordId == null || at == null
                || blankOrLonger(problem, 500) || blankOrLonger(openingReason, 1000)) {
            throw invalid("A course needs a problem up to 500 characters, a reason up to 1000 and its first visit");
        }
        return new TreatmentCourse(UUID.randomUUID(), patientId, departmentId, problem.strip(), openingReason.strip(),
                doctorId, TreatmentCourseStatus.OPEN, List.of(firstRecordId), at, null, null, null, null);
    }

    public static TreatmentCourse restore(UUID courseId, UUID patientId, UUID departmentId, String problem,
            String openingReason, UUID responsibleDoctorId, TreatmentCourseStatus status, List<UUID> recordIds,
            Instant openedAt, TreatmentCourseDecision closureDecision, String closureNote, UUID closedByStaffId,
            Instant closedAt) {
        return new TreatmentCourse(Objects.requireNonNull(courseId), patientId, departmentId, problem, openingReason,
                responsibleDoctorId, Objects.requireNonNull(status), recordIds, openedAt, closureDecision, closureNote,
                closedByStaffId, closedAt);
    }

    /** @return false when the visit is already part of this course */
    public boolean addVisit(UUID recordId, UUID staffId, Instant at) {
        requireOpen();
        if (recordId == null || staffId == null || at == null) {
            throw invalid("A visit and the doctor adding it are required");
        }
        if (recordIds.contains(recordId)) {
            return false;
        }
        recordIds.add(recordId);
        return true;
    }

    public void close(TreatmentCourseDecision decision, String note, UUID staffId, Instant at) {
        requireOpen();
        if (decision == null || staffId == null || at == null || blankOrLonger(note, 1000)) {
            throw invalid("Closing a course needs a decision and a note up to 1000 characters");
        }
        status = TreatmentCourseStatus.CLOSED;
        closureDecision = decision;
        closureNote = note.strip();
        closedByStaffId = staffId;
        closedAt = at;
    }

    /** Only a hint for review: nothing closes a course except a doctor's decision. */
    public static boolean reviewDue(LocalDate today, LocalDate lastVisitDate) {
        return today != null && lastVisitDate != null && lastVisitDate.plusDays(REVIEW_AFTER_DAYS).isBefore(today);
    }

    private void requireOpen() {
        if (status != TreatmentCourseStatus.OPEN) {
            throw new InvalidClinicalDataException("TREATMENT_COURSE_CLOSED", "This treatment course is closed");
        }
    }

    private static boolean blankOrLonger(String value, int max) {
        return value == null || value.isBlank() || value.strip().length() > max;
    }

    private static InvalidClinicalDataException invalid(String message) {
        return new InvalidClinicalDataException("TREATMENT_COURSE_INVALID", message);
    }

    public UUID getCourseId() { return courseId; }
    public UUID getPatientId() { return patientId; }
    public UUID getDepartmentId() { return departmentId; }
    public String getProblem() { return problem; }
    public String getOpeningReason() { return openingReason; }
    public UUID getResponsibleDoctorId() { return responsibleDoctorId; }
    public TreatmentCourseStatus getStatus() { return status; }
    public List<UUID> getRecordIds() { return List.copyOf(recordIds); }
    public Instant getOpenedAt() { return openedAt; }
    public TreatmentCourseDecision getClosureDecision() { return closureDecision; }
    public String getClosureNote() { return closureNote; }
    public UUID getClosedByStaffId() { return closedByStaffId; }
    public Instant getClosedAt() { return closedAt; }
}
