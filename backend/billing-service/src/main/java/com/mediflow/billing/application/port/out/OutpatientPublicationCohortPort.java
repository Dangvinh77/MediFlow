package com.mediflow.billing.application.port.out;

import java.util.UUID;

/**
 * Enrolls a brand-new outpatient PAYMENT_REQUEST into the V1 publication cohort, MANDATORY in the
 * same transaction as its save. A no-op whenever the request/mode/flags/purpose make it ineligible
 * — this is the normal case while the global flag is off, never an error.
 */
public interface OutpatientPublicationCohortPort {
    void enrollIfEligible(UUID paymentRequestId);
}
