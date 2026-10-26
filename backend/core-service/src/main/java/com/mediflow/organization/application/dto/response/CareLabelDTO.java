package com.mediflow.organization.application.dto.response;

import java.util.UUID;

/** Display identity only. Not an eligibility, scheduling or authorization proof. */
public record CareLabelDTO(UUID id, String label) {}
