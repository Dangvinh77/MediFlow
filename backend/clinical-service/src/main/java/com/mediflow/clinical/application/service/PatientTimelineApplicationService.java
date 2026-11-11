package com.mediflow.clinical.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.clinical.application.dto.response.RecordDocumentDTO;
import com.mediflow.clinical.application.dto.response.TimelineEntryDTO;
import com.mediflow.clinical.application.port.out.ExternalResultRepositoryPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.PatientTimelineReadPort;
import com.mediflow.clinical.application.port.out.PatientTimelineReadPort.TimelineRow;
import com.mediflow.clinical.application.port.out.RecordAccessPolicyMode;
import com.mediflow.clinical.application.port.out.TreatmentCourseMode;
import com.mediflow.clinical.application.port.out.TreatmentCourseRepositoryPort;
import com.mediflow.clinical.domain.exception.MedicalRecordNotFoundException;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.MedicalRecordStatus;
import com.mediflow.common.api.PageResult;

/** The patient's visit history and the documents of one visit; read rules match the record list. */
@Service
public class PatientTimelineApplicationService {
    static final int MAX_PAGE_SIZE = 50;

    private final PatientTimelineReadPort timeline;
    private final MedicalRecordRepositoryPort records;
    private final ExternalResultRepositoryPort results;
    private final RecordReadGuard readGuard;
    private final boolean recordAccessPolicyEnabled;
    private final TreatmentCourseRepositoryPort courses;
    private final boolean coursesEnabled;

    @Autowired
    public PatientTimelineApplicationService(PatientTimelineReadPort timeline, MedicalRecordRepositoryPort records,
            ExternalResultRepositoryPort results, RecordReadGuard readGuard, RecordAccessPolicyMode mode,
            TreatmentCourseRepositoryPort courses, TreatmentCourseMode courseMode) {
        this(timeline, records, results, readGuard, mode.enabled(), courses, courseMode.enabled());
    }

    public PatientTimelineApplicationService(PatientTimelineReadPort timeline, MedicalRecordRepositoryPort records,
            ExternalResultRepositoryPort results, RecordReadGuard readGuard, boolean recordAccessPolicyEnabled) {
        this(timeline, records, results, readGuard, recordAccessPolicyEnabled, null, false);
    }

    public PatientTimelineApplicationService(PatientTimelineReadPort timeline, MedicalRecordRepositoryPort records,
            ExternalResultRepositoryPort results, RecordReadGuard readGuard, boolean recordAccessPolicyEnabled,
            TreatmentCourseRepositoryPort courses, boolean coursesEnabled) {
        this.timeline = timeline;
        this.records = records;
        this.results = results;
        this.readGuard = readGuard;
        this.recordAccessPolicyEnabled = recordAccessPolicyEnabled;
        this.courses = courses;
        this.coursesEnabled = coursesEnabled && courses != null;
    }

    @Transactional(readOnly = true)
    public PageResult<TimelineEntryDTO> timeline(UUID patientId, int page, int size, UUID departmentId,
            MedicalRecordStatus status) {
        UUID nurseDepartmentId = recordAccessPolicyEnabled
                ? readGuard.requirePatient(patientId).nurseDepartmentId()
                : null;
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        var result = timeline.page(patientId, departmentId, nurseDepartmentId, status == null ? null : status.name(),
                safePage, safeSize);
        Map<UUID, List<UUID>> courseIds = coursesEnabled
                ? courses.courseIdsByRecord(result.rows().stream().map(TimelineRow::recordId).toList())
                : null;
        return PageResult.of(result.rows().stream().map(row -> entry(row, courseIds)).toList(),
                result.total(), safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public List<RecordDocumentDTO> documents(UUID recordId) {
        MedicalRecord record = records.findById(recordId)
                .orElseThrow(() -> new MedicalRecordNotFoundException("Medical record not found: " + recordId));
        if (recordAccessPolicyEnabled) {
            readGuard.requireRecord(record);
        }
        Instant concludedAt = record.getCompletedAt() != null ? record.getCompletedAt() : record.getClosedAt();
        return results.findByRecord(recordId).stream()
                .map(document -> new RecordDocumentDTO(document.type(), document.referenceId(), document.summary(),
                        document.attachedAt(), concludedAt != null && document.attachedAt().isAfter(concludedAt)))
                .toList();
    }

    private static TimelineEntryDTO entry(TimelineRow row, Map<UUID, List<UUID>> courseIds) {
        UUID responsible = Objects.equals(row.responsibleDoctorId(), row.doctorId()) ? null : row.responsibleDoctorId();
        return new TimelineEntryDTO(row.recordId(), row.examinationDate(), row.departmentId(), row.doctorId(),
                responsible, visitKind(row), row.status(), row.disposition(), row.previousRecordId(),
                courseIds == null ? null : courseIds.getOrDefault(row.recordId(), List.of()));
    }

    private static String visitKind(TimelineRow row) {
        if ("FOLLOW_UP".equals(row.visitType())) {
            return "FOLLOW_UP";
        }
        if (row.previousRecordId() != null) {
            return "RETURN";
        }
        return row.visitType() == null ? null : "NEW";
    }
}
