package com.mediflow.report.infrastructure.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.report.domain.exception.ReportRuleException;
import com.mediflow.report.domain.model.FinancialEvidenceArchive;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class FinancialArchiveJsonCodecTest {
    private final FinancialArchiveJsonCodec codec = new FinancialArchiveJsonCodec();
    private FinancialEvidenceArchive archive() {
        var entry = new FinancialEvidenceArchive.Entry(FinancialEvidenceArchive.SourceKind.REFUND, UUID.randomUUID(), UUID.randomUUID(), FinancialEvidenceArchive.SourceState.PENDING, "a".repeat(64),
                "b".repeat(64), "{\"amount\":10.10,\"completedAt\":\"2026-10-09T23:58:50.123456789Z\"}",
                FinancialEvidenceArchive.sha256("{\"amount\":10.10,\"completedAt\":\"2026-10-09T23:58:50.123456789Z\"}"));
        var entries = List.of(entry);
        return FinancialEvidenceArchive.create(UUID.randomUUID(), LocalDate.of(2026,10,9), LocalDate.of(2026,10,10),
                "Asia/Bangkok", Instant.parse("2026-10-11T00:00:00.123456Z"), 1, FinancialEvidenceArchive.manifestHash(entries), entries);
    }
    @Test void roundTrip_preservesExactEvidenceBytesAndUnresolvedState() {
        var source = archive(); assertThat(codec.decode(codec.encode(source))).isEqualTo(source);
        assertThat(new String(codec.encode(source), java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("acceptanceReference", "publicationId");
    }
    @ParameterizedTest @ValueSource(strings={"version","origin","coverage","zone","observed","manifest","archive","state","first-event","snapshot","source-hash","null-entries","count","unknown-field","fractional-version","numeric-state","numeric-kind","unknown-state"})
    void decode_damagedMetadataOrEvidence_rejects(String mutation) throws Exception {
        ObjectNode document = (ObjectNode) new ObjectMapper().readTree(codec.encode(archive()));
        var entry = (ObjectNode) document.path("entries").get(0);
        switch (mutation) {
            case "version" -> document.put("archiveVersion", 2);
            case "origin" -> document.put("originGenerationId", UUID.randomUUID().toString());
            case "coverage" -> document.put("coveredFrom", "2026-10-08");
            case "zone" -> document.put("reportZone", "UTC");
            case "observed" -> document.put("sourceObservedAt", "2026-10-12T00:00:00Z");
            case "manifest" -> document.put("manifestFingerprint", "f".repeat(64));
            case "archive" -> document.put("archiveFingerprint", "f".repeat(64));
            case "state" -> entry.put("sourceState", "APPLIED");
            case "first-event" -> entry.put("firstEventId", UUID.randomUUID().toString());
            case "snapshot" -> entry.put("factSnapshot", "changed");
            case "source-hash" -> entry.put("sourceFingerprint", "f".repeat(64));
            case "null-entries" -> document.putNull("entries");
            case "count" -> document.put("unresolvedInputs", 0);
            case "unknown-field" -> document.put("acceptedHistory", true);
            case "fractional-version" -> document.put("archiveVersion", 1.5);
            case "numeric-state" -> entry.put("sourceState", 1);
            case "numeric-kind" -> entry.put("sourceKind", 3);
            case "unknown-state" -> entry.put("sourceState", "PUBLISHED");
            default -> throw new IllegalArgumentException();
        }
        assertThatThrownBy(() -> codec.decode(new ObjectMapper().writeValueAsBytes(document))).isInstanceOf(ReportRuleException.class);
    }
    @Test void decode_duplicateKeysTrailingTokensAndMissingFields_rejects() {
        String bytes = new String(codec.encode(archive()), java.nio.charset.StandardCharsets.UTF_8);
        for (String bad : List.of(bytes.replaceFirst("\\{", "{\"archiveVersion\":1,"), bytes + "{}", "{}")) {
            assertThatThrownBy(() -> codec.decode(bad.getBytes(java.nio.charset.StandardCharsets.UTF_8))).isInstanceOf(ReportRuleException.class);
        }
    }
    @Test void decode_oversizedOrEmptyInput_rejectsBeforeParsing() {
        assertThatThrownBy(() -> codec.decode(new byte[FinancialEvidenceArchive.MAX_WIRE_BYTES+1])).isInstanceOf(ReportRuleException.class);
        assertThatThrownBy(() -> codec.decode(new byte[0])).isInstanceOf(ReportRuleException.class);
        assertThatThrownBy(() -> codec.decode(null)).isInstanceOf(ReportRuleException.class);
    }
    @Test void create_duplicateSourceOrUnsortedEntries_rejects() {
        var archive = archive(); var first = archive.entries().getFirst();
        assertThatThrownBy(() -> FinancialEvidenceArchive.create(archive.originGenerationId(), archive.coveredFrom(), archive.coveredTo(),
                archive.reportZone(), archive.sourceObservedAt(), 2, FinancialEvidenceArchive.manifestHash(List.of(first,first)), List.of(first,first)))
                .isInstanceOf(ReportRuleException.class);
        var second = new FinancialEvidenceArchive.Entry(FinancialEvidenceArchive.SourceKind.FINAL, UUID.randomUUID(), UUID.randomUUID(), FinancialEvidenceArchive.SourceState.PENDING, first.sourceFingerprint(),
                first.envelopeFingerprint(), first.factSnapshot(), first.snapshotFingerprint());
        var unsorted = List.of(first,second);
        assertThatThrownBy(() -> FinancialEvidenceArchive.create(archive.originGenerationId(), archive.coveredFrom(), archive.coveredTo(),
                archive.reportZone(), archive.sourceObservedAt(), 2, FinancialEvidenceArchive.manifestHash(unsorted), unsorted)).isInstanceOf(ReportRuleException.class);
    }
    @Test void create_subMicrosecondObservation_rejectsInsteadOfSilentlyTruncatingAtRestore() {
        var archive = archive();
        assertThatThrownBy(() -> FinancialEvidenceArchive.create(archive.originGenerationId(),archive.coveredFrom(),archive.coveredTo(),
                archive.reportZone(),Instant.parse("2026-10-11T00:00:00.123456789Z"),archive.unresolvedInputs(),archive.manifestFingerprint(),archive.entries()))
                .isInstanceOf(ReportRuleException.class);
    }
}
