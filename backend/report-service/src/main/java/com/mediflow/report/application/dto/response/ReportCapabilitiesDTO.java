package com.mediflow.report.application.dto.response;

import java.util.List;

/** No source payloads/identifiers: explicit supported contract versus future capabilities. */
public record ReportCapabilitiesDTO(int capabilityVersion, String historyPolicy,
        List<SourceCapability> sources, List<String> unsupportedFeatures) {
    public record SourceCapability(String eventType, String producer, int envelopeVersion,
            String sourceIdField, String revisionPolicy, String correctionPolicy) { }
}
