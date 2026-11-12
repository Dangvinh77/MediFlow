package com.mediflow.clinical.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.clinical.domain.model.TreatmentCourse;

public interface TreatmentCourseRepositoryPort {
    void insert(TreatmentCourse course);

    Optional<TreatmentCourse> findByIdForUpdate(UUID courseId);

    Optional<CourseView> view(UUID courseId);

    /** Newest first. */
    List<CourseView> findByPatient(UUID patientId);

    /** @return false when the visit was already linked */
    boolean addVisit(UUID courseId, UUID recordId, UUID staffId, Instant at);

    void close(TreatmentCourse course);

    Map<UUID, List<UUID>> courseIdsByRecord(Collection<UUID> recordIds);

    /** A course with the examination date of its latest visit. */
    record CourseView(TreatmentCourse course, LocalDate lastVisitDate) {}
}
