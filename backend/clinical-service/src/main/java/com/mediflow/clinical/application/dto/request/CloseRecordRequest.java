package com.mediflow.clinical.application.dto.request;

import com.mediflow.clinical.domain.model.RecordClosureReason;

/** Why a visit ends without a conclusion; {@code note} also records the advice already given to the patient. */
public record CloseRecordRequest(RecordClosureReason reason, String note) {
}
