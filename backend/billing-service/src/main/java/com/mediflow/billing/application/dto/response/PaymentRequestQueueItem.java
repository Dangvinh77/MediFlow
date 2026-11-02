package com.mediflow.billing.application.dto.response;

import java.time.Instant;

/** Billing issuance time; never a visit date or payment authority. */
public record PaymentRequestQueueItem(PaymentRequestDiscoveryDTO request, Instant createdAt) {}
