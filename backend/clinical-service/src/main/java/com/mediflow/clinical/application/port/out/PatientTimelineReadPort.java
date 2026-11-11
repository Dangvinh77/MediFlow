package com.mediflow.clinical.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Reads one page of a patient's visits, newest first, without loading clinical content. */
public interface PatientTimelineReadPort {

    /**
     * @param departmentId optional filter chosen by the reader
     * @param nurseDepartmentId when not null, only visits of this department are visible
     * @param status optional {@code MedicalRecordStatus} name
     */
    TimelinePage page(UUID patientId, UUID departmentId, UUID nurseDepartmentId, String status, int page, int size);

    record TimelineRow(UUID recordId, LocalDate examinationDate, UUID departmentId, UUID doctorId,
            UUID responsibleDoctorId, String visitType, String status, String disposition, UUID previousRecordId) {}

    record TimelinePage(List<TimelineRow> rows, long total) {
        public TimelinePage {
            rows = List.copyOf(rows);
        }
    }
}
