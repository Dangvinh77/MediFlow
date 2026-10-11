package com.mediflow.report.domain.model;

import com.mediflow.report.domain.exception.ReportRuleException;
import java.util.UUID;

/** Finite evidence is publishable; unavailable history is never represented as zero. */
public final class FinancialPublicationPolicy {
    public enum HistoryPolicy { AVAILABLE_EVIDENCE_ONLY }

    private FinancialPublicationPolicy() { }

    public static void requireCommand(UUID commandId, UUID generationId, UUID actorId, String reference) {
        if (commandId == null || generationId == null || actorId == null || reference == null
                || !reference.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,119}")) {
            throw new ReportRuleException("REPORT_PUBLICATION_INVALID", "An identified operator and bounded evidence reference are required");
        }
    }

    public static void requireEvidence(String fingerprint, HistoryPolicy historyPolicy) {
        if (fingerprint == null || !fingerprint.matches("[a-f0-9]{64}")
                || historyPolicy != HistoryPolicy.AVAILABLE_EVIDENCE_ONLY) {
            throw new ReportRuleException("REPORT_PUBLICATION_INVALID", "Explicit finite-evidence history policy and manifest proof are required");
        }
    }
}
