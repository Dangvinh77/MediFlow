package com.mediflow.clinical.application.port.out;

import java.util.UUID;

import com.mediflow.clinical.domain.model.ExternalResultType;

/** Stores external references separately from the medical record's clinical text. */
public interface ExternalResultRepositoryPort {

    /** Repeating the same record, type and reference must have no additional effect. */
    void attach(UUID recordId, ExternalResultType type, UUID referenceId, String summary);
}
