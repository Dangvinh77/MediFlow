package com.mediflow.billing.application.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Exact A.10 body; payment amount and method are derived from Billing's stored quote. */
public record LabZeroPayableEligibilityRequest(@NotNull UUID quoteId) { }
