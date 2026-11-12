package com.mediflow.report.application.mapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import com.mediflow.report.application.dto.command.carefinance.ApplyOperationalContributionCommand;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.domain.model.OperationalContribution;
import com.mediflow.report.domain.model.OperationalContribution.Metric;

/**
 * Pure V1 Lab source mapping, still offline. Uses the producer's resultVersion, labId,
 * requesting department, exact episode and completedAt; never event version/date or record fallback.
 * Correction/replacement versions remain unsupported by the contribution kernel.
 */
public class LabOperationalContributionMapper {
    private final ZoneId reportZone;

    public LabOperationalContributionMapper(ZoneId reportZone) {
        this.reportZone = reportZone;
    }

    public ApplyOperationalContributionCommand map(DecodedCareFinanceEvent event) {
        var metadata = event.metadata();
        if (metadata.version() != 1 || !"lab.result.created".equals(metadata.eventType())
                || !"lab-service".equals(metadata.producer()) || !"labId".equals(metadata.sourceField())) {
            throw new IllegalArgumentException("Only canonical V1 Lab result facts can map to LAB_TESTS");
        }
        var payload = event.payload();
        OperationalSourceFields.rejectCorrectionMarkers(payload);
        if (payload.containsKey("sourceRevision")) throw new IllegalArgumentException("Lab uses only explicit resultVersion");
        UUID sourceId = uuid(payload, "labId");
        if (!sourceId.equals(metadata.sourceId())) {
            throw new IllegalArgumentException("Lab source identity differs from decoded metadata");
        }
        int revision = revision(payload.get("resultVersion"));
        UUID departmentId = uuid(payload, "departmentId");
        String episodeType = text(payload, "careEpisodeType");
        UUID episodeId = uuid(payload, "careEpisodeId");
        Instant completedAt = Instant.parse(text(payload, "completedAt"));
        var fact = new OperationalContribution(metadata.eventId(), "LAB_RESULT", sourceId, revision, Metric.LAB_TESTS,
                departmentId, episodeType, episodeId, completedAt.atZone(reportZone).toLocalDate(),
                BigDecimal.ONE, null, completedAt);
        return new ApplyOperationalContributionCommand(event, fact);
    }

    /**
     * {@code lab.result.amended} v1 corrects a result already counted under revision 1: it is checked and
     * acknowledged but contributes nothing, so an amendment never counts as another test.
     */
    public void validateAmendment(DecodedCareFinanceEvent event) {
        var metadata = event.metadata();
        if (metadata.version() != 1 || !"lab.result.amended".equals(metadata.eventType())
                || !"lab-service".equals(metadata.producer()) || !"labId".equals(metadata.sourceField())) {
            throw new IllegalArgumentException("Only canonical V1 Lab amendments are accepted");
        }
        var payload = event.payload();
        if (!uuid(payload, "labId").equals(metadata.sourceId())) {
            throw new IllegalArgumentException("Lab source identity differs from decoded metadata");
        }
        Object version = payload.get("resultVersion");
        Object previous = payload.get("previousResultVersion");
        if (!(version instanceof Integer v) || !(previous instanceof Integer p) || v < 2 || p < 1 || p >= v) {
            throw new IllegalArgumentException("Lab amendment revisions are invalid");
        }
        text(payload, "amendmentReason");
        uuid(payload, "departmentId");
        uuid(payload, "careEpisodeId");
        if (!"OUTPATIENT_VISIT".equals(text(payload, "careEpisodeType"))) {
            throw new IllegalArgumentException("Lab amendment care episode is unsupported");
        }
        Instant.parse(text(payload, "releasedAt"));
    }

    private static int revision(Object value) {
        if (!(value instanceof Integer || value instanceof Long || value instanceof BigInteger)) {
            throw new IllegalArgumentException("Lab resultVersion must be an explicit integral source revision");
        }
        try {
            int revision = new BigInteger(value.toString()).intValueExact();
            if (revision != 1) throw new IllegalArgumentException("Lab corrections require a versioned source contract");
            return revision;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Lab resultVersion must be a positive bounded source revision", exception);
        }
    }

    private static String text(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(field + " is required in the Lab source fact");
        }
        return text;
    }

    private static UUID uuid(Map<String, Object> payload, String field) {
        return UUID.fromString(text(payload, field));
    }
}
