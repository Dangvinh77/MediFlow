package com.mediflow.clinical.application.dto.request;

/** {@code status} is RESOLVED or ENTERED_IN_ERROR. */
public record AllergyStatusRequest(String status, String reason) {
}
