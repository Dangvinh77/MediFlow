package com.mediflow.clinical.application.port.out;

import com.mediflow.clinical.domain.model.RecordAccessLogEntry;

public interface RecordAccessLogPort {
    /** Commits independently of the caller's transaction so a denied read stays recorded. */
    void append(RecordAccessLogEntry entry);
}
