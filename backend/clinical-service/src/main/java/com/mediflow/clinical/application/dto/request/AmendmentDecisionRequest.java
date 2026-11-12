package com.mediflow.clinical.application.dto.request;

/** {@code decision} is {@code APPROVED} or {@code REJECTED}. */
public record AmendmentDecisionRequest(String decision, String note) {
}
