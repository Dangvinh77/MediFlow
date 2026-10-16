package com.mediflow.billing.application.dto.command;

/**
 * The three outpatient purposes this discovery boundary accepts (HANDOFF-LOC-OUTPATIENT-DISCOVERY-
 * PUBLICATION.md §3). Deferred purposes (ADMISSION_DEPOSIT, SURGERY, SETTLEMENT) are out of scope
 * for {@code GET /api/v1/billing/payment-requests/by-source} and are rejected before reaching this type.
 */
public enum PaymentRequestSourcePurpose { EXAM, LAB_TEST, PRESCRIPTION }
