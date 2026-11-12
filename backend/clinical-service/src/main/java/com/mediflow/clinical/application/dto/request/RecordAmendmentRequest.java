package com.mediflow.clinical.application.dto.request;

import com.mediflow.clinical.domain.model.AmendmentSection;

public record RecordAmendmentRequest(AmendmentSection section, String amendedText, String reason) {
}
