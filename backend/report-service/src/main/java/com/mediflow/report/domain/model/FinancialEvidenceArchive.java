package com.mediflow.report.domain.model;

import com.mediflow.report.domain.exception.ReportRuleException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Portable local evidence only. Digests detect damage; they are not producer signatures or coverage approval. */
public record FinancialEvidenceArchive(int archiveVersion, UUID originGenerationId, LocalDate coveredFrom,
        LocalDate coveredTo, String reportZone, Instant sourceObservedAt, int unresolvedInputs,
        String manifestFingerprint, List<Entry> entries, String archiveFingerprint) {
    public static final int MAX_WIRE_BYTES = 32 * 1024 * 1024;
    private static final Comparator<Entry> ORDER = Comparator.comparing((Entry entry) -> entry.sourceKind().name())
            .thenComparing(entry -> entry.sourceId().toString());

    public enum SourceKind { BALANCE, FINAL, RECEIPT, REFUND }
    public enum SourceState { APPLIED, PENDING, REJECTED }

    public record Entry(SourceKind sourceKind, UUID sourceId, UUID firstEventId, SourceState sourceState,
            String sourceFingerprint, String envelopeFingerprint, String factSnapshot, String snapshotFingerprint) {
        public Entry {
            require(sourceKind != null && sourceId != null && firstEventId != null && sourceState != null);
            require(digest(sourceFingerprint) && digest(envelopeFingerprint) && digest(snapshotFingerprint));
            require(factSnapshot != null && factSnapshot.getBytes(StandardCharsets.UTF_8).length <= 1_048_576);
            require(sha256(factSnapshot).equals(snapshotFingerprint));
        }
    }

    public FinancialEvidenceArchive {
        require(archiveVersion == 1 && originGenerationId != null && coveredFrom != null && coveredTo != null
                && coveredFrom.getYear() >= 2000 && coveredTo.getYear() <= 2100 && !coveredTo.isBefore(coveredFrom)
                && ChronoUnit.DAYS.between(coveredFrom, coveredTo) < 366 && reportZone != null && reportZone.length() <= 80
                && sourceObservedAt != null && sourceObservedAt.getNano() % 1000 == 0);
        try {
            require(!sourceObservedAt.isBefore(coveredTo.plusDays(1).atStartOfDay(ZoneId.of(reportZone)).toInstant()));
        } catch (java.time.DateTimeException error) { throw invalid(); }
        require(entries != null && entries.size() <= 10_000 && entries.stream().noneMatch(java.util.Objects::isNull));
        entries = List.copyOf(entries);
        require(entries.equals(entries.stream().sorted(ORDER).toList()));
        var keys = new HashSet<String>();
        for (var entry : entries) require(keys.add(entry.sourceKind() + ":" + entry.sourceId()));
        require(entries.stream().mapToLong(entry -> entry.factSnapshot().getBytes(StandardCharsets.UTF_8).length).sum() <= 16 * 1024 * 1024);
        require(unresolvedInputs == entries.stream().filter(entry -> entry.sourceState() != SourceState.APPLIED).count());
        require(digest(manifestFingerprint) && manifestFingerprint.equals(manifestHash(entries)));
        require(digest(archiveFingerprint) && archiveFingerprint.equals(archiveHash(originGenerationId, coveredFrom,
                coveredTo, reportZone, sourceObservedAt, unresolvedInputs, manifestFingerprint)));
    }

    public static FinancialEvidenceArchive create(UUID origin, LocalDate from, LocalDate to, String zone,
            Instant observedAt, int unresolved, String manifest, List<Entry> entries) {
        return new FinancialEvidenceArchive(1, origin, from, to, zone, observedAt, unresolved, manifest, entries,
                archiveHash(origin, from, to, zone, observedAt, unresolved, manifest));
    }

    private static String archiveHash(UUID origin, LocalDate from, LocalDate to, String zone, Instant observed,
            int unresolved, String manifest) {
        // Length prefixes bind metadata without delimiter ambiguity. Entry hashes include exact snapshot bytes.
        var proof = new StringBuilder();
        for (var value : new Object[] {"REPORT_LOCAL_FINANCIAL_ARCHIVE_V1", origin, from, to, zone, observed, unresolved, manifest}) {
            String text = String.valueOf(value); proof.append(text.length()).append(':').append(text);
        }
        return sha256(proof.toString());
    }

    public static String manifestHash(List<Entry> entries) {
        var proof = new StringBuilder();
        for (var entry : entries) proof.append(entry.sourceKind()).append(':').append(entry.sourceId()).append(':')
                .append(entry.firstEventId()).append(':').append(entry.sourceState()).append(':')
                .append(entry.sourceFingerprint()).append(':').append(entry.envelopeFingerprint()).append(':')
                .append(entry.snapshotFingerprint()).append('\n');
        return sha256(proof.toString());
    }

    private static boolean digest(String value) { return value != null && value.matches("[a-f0-9]{64}"); }
    public static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException("SHA-256 unavailable", error); }
    }
    private static void require(boolean condition) { if (!condition) throw invalid(); }
    private static ReportRuleException invalid() {
        return new ReportRuleException("REPORT_FINANCIAL_ARCHIVE_INVALID", "Invalid or damaged bounded financial evidence archive");
    }
}
