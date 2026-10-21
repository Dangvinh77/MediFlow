package com.mediflow.report.application.port.out;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** A local witness is compared with reviewed exports; it never attests upstream completeness. */
public interface OutpatientCoverageEvidencePort {
    Snapshot inspect(LocalDate from, LocalDate to, UUID department, String zone, String currency);

    record SourceWitness(long rowCount, String fingerprint) { }
    record Snapshot(String fingerprint, boolean pendingOrInvalid, Map<String, SourceWitness> sources) {
        public Snapshot { sources = Map.copyOf(sources); }
    }
}
