package com.mediflow.report.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.mediflow.report.domain.exception.ReportRuleException;

/** An explicit business fact, not a guessed mapping from a delivery ID or clinical text. */
public record OperationalContribution(
        UUID eventId, String sourceType, UUID sourceId, int sourceRevision, Metric metric,
        UUID departmentId, String careEpisodeType, UUID careEpisodeId,
        LocalDate metricDate, BigDecimal value, String category, Instant occurredAt) {

    public enum Metric {
        COMPLETED_VISITS, ADMISSIONS, DISCHARGES, LAB_TESTS, DISPENSED_PRESCRIPTIONS, DISPENSED_UNITS,
        SURGERIES_COMPLETED, SURGERIES_CANCELLED, SURGERY_DURATION_MINUTES;

        /** DTO field keys remain English camelCase; storage/source enum tags do not change. */
        public String wireName() {
            return switch (this) {
                case COMPLETED_VISITS -> "completedVisits";
                case ADMISSIONS -> "admissions";
                case DISCHARGES -> "discharges";
                case LAB_TESTS -> "labTests";
                case DISPENSED_PRESCRIPTIONS -> "dispensedPrescriptions";
                case DISPENSED_UNITS -> "dispensedUnits";
                case SURGERIES_COMPLETED -> "surgeriesCompleted";
                case SURGERIES_CANCELLED -> "surgeriesCancelled";
                case SURGERY_DURATION_MINUTES -> "surgeryDurationMinutes";
            };
        }
    }

    public OperationalContribution {
        if (eventId == null || sourceType == null || !sourceType.matches("[A-Z_]{1,40}")
                || sourceId == null || sourceRevision < 1 || metric == null || departmentId == null
                || metricDate == null || value == null || value.signum() < 0 || occurredAt == null
                || (careEpisodeType == null) != (careEpisodeId == null)
                || (careEpisodeType != null && !careEpisodeType.equals("OUTPATIENT_VISIT")
                    && !careEpisodeType.equals("ADMISSION"))
                || (category != null && (category.isBlank() || category.length() > 80))) {
            throw invalid("Incomplete operational contribution");
        }
        try {
            value = value.setScale(0, RoundingMode.UNNECESSARY);
            value.longValueExact();
            if (value.precision() > 16) {
                throw new ArithmeticException("Value exceeds DECIMAL(19,3) storage precision");
            }
        } catch (ArithmeticException exception) {
            throw invalid("Operational counts/duration must be an exact nonnegative BIGINT");
        }
        // These are one business operation, not arbitrary client-supplied counters.
        if (metric != Metric.DISPENSED_UNITS && metric != Metric.SURGERY_DURATION_MINUTES
                && value.compareTo(BigDecimal.ONE) != 0) {
            throw invalid("A completed business operation contributes exactly one count");
        }
    }

    private static ReportRuleException invalid(String message) {
        return new ReportRuleException("OPERATIONAL_CONTRIBUTION_INVALID", message);
    }
}
