package com.mediflow.billing.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Applies to new refunds only; historical HELD facts remain immutable. */
@ConfigurationProperties(prefix = "mediflow.billing.outpatient-refund-publication")
public record OutpatientRefundPublicationProperties(boolean enabled, String acceptanceReference) {
    public OutpatientRefundPublicationProperties {
        if (enabled && (acceptanceReference == null || acceptanceReference.isBlank()
                || acceptanceReference.length() > 200)) {
            throw new IllegalArgumentException("outpatient-refund-publication requires an acceptance-reference of 1-200 characters");
        }
    }
}
