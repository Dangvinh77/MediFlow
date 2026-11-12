package com.mediflow.clinical.application.dto.request;

import java.util.UUID;

/** {@code severity} is MILD, MODERATE, SEVERE or UNKNOWN; absent means UNKNOWN. */
public record AddAllergyRequest(String substance, String reaction, String severity, UUID sourceRecordId) {
}
