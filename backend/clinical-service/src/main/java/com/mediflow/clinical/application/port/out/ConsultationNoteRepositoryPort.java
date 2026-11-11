package com.mediflow.clinical.application.port.out;

import java.util.List;
import java.util.UUID;

import com.mediflow.clinical.domain.model.ConsultationNote;

/** Notes are appended and read; there is deliberately no update or delete. */
public interface ConsultationNoteRepositoryPort {
    void insert(ConsultationNote note);

    /** Oldest first. */
    List<ConsultationNote> findByRecord(UUID recordId);
}
