package com.mediflow.billing.infrastructure.config;

import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code mediflow.billing.outpatient-publication.*} (HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md
 * §4). Default-off: {@code enabled=false}, empty {@code purposes}. A purpose outside the three
 * discoverable outpatient purposes, or a blank acceptance reference while enabled, fails startup —
 * the global flag never silently admits a deferred purpose.
 */
@ConfigurationProperties(prefix = "mediflow.billing.outpatient-publication")
public record OutpatientPublicationProperties(boolean enabled, List<String> purposes, String acceptanceReference) {
    private static final Set<String> ALLOWED_PURPOSES = Set.of("EXAM", "LAB_TEST", "PRESCRIPTION");

    public OutpatientPublicationProperties {
        purposes = purposes == null ? List.of() : List.copyOf(purposes);
        for (String purpose : purposes) {
            if (!ALLOWED_PURPOSES.contains(purpose)) {
                throw new IllegalArgumentException(
                        "mediflow.billing.outpatient-publication.purposes only accepts EXAM, LAB_TEST, PRESCRIPTION: " + purpose);
            }
        }
        if (enabled && (acceptanceReference == null || acceptanceReference.isBlank())) {
            throw new IllegalArgumentException(
                    "mediflow.billing.outpatient-publication.acceptance-reference is required when enabled=true");
        }
    }
}
