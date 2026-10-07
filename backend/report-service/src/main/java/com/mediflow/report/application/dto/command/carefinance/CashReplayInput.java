package com.mediflow.report.application.dto.command.carefinance;

import java.util.UUID;

import com.mediflow.report.domain.model.CashReceipt;

/** Accepted minimal receipt with frozen provenance hashes, not a reconstructed producer payload. */
public record CashReplayInput(CashReceipt receipt, UUID firstEventId, String factFingerprint,
        String payloadFingerprint, String firstEnvelopeFingerprint) {
    public CashReplayInput {
        if (receipt == null || firstEventId == null || !validHash(factFingerprint)
                || !validHash(payloadFingerprint) || !validHash(firstEnvelopeFingerprint)) {
            throw new IllegalArgumentException("Complete frozen cash source evidence is required");
        }
    }

    private static boolean validHash(String hash) { return hash != null && hash.matches("[a-f0-9]{64}"); }
}
