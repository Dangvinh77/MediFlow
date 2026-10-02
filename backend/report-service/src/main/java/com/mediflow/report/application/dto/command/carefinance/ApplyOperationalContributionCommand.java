package com.mediflow.report.application.dto.command.carefinance;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import com.mediflow.report.domain.model.OperationalContribution;

/** Source revision is explicitly supplied; no event version -> business revision fallback. */
public record ApplyOperationalContributionCommand(
        DecodedCareFinanceEvent event, List<OperationalContribution> contributions) {
    public ApplyOperationalContributionCommand(DecodedCareFinanceEvent event, OperationalContribution contribution) {
        this(event, List.of(contribution));
    }

    public ApplyOperationalContributionCommand {
        if (event == null || event.metadata() == null || contributions == null || contributions.isEmpty()) {
            throw new IllegalArgumentException("Event and at least one operational contribution are required");
        }
        contributions = List.copyOf(contributions);
        var first = contributions.get(0);
        var metrics = new HashSet<OperationalContribution.Metric>();
        for (var contribution : contributions) {
            if (!contribution.eventId().equals(event.metadata().eventId())
                    || !contribution.sourceId().equals(event.metadata().sourceId())
                    || !metrics.add(contribution.metric())) {
                throw new IllegalArgumentException("Each event/source metric must occur exactly once");
            }
            if (!contribution.departmentId().equals(first.departmentId())
                    || !Objects.equals(contribution.careEpisodeType(), first.careEpisodeType())
                    || !Objects.equals(contribution.careEpisodeId(), first.careEpisodeId())
                    || !contribution.metricDate().equals(first.metricDate())
                    || !contribution.occurredAt().equals(first.occurredAt())) {
                throw new IllegalArgumentException("Metrics from one event must share the exact context/time snapshot");
            }
            // New result/outcome versions are corrections, not another completed operation.
            if (contribution.sourceRevision() != 1) {
                throw new IllegalArgumentException("Operational corrections require an explicit replacement/reversal contract");
            }
            String expectedEventType = switch (contribution.metric()) {
                case COMPLETED_VISITS -> "medicalrecord.completed";
                case ADMISSIONS -> "admission.started";
                case DISCHARGES -> "admission.closed";
                case LAB_TESTS -> "lab.result.created";
                case DISPENSED_PRESCRIPTIONS, DISPENSED_UNITS -> "prescription.filled";
                case SURGERIES_COMPLETED, SURGERY_DURATION_MINUTES -> "surgery.completed";
                case SURGERIES_CANCELLED -> "surgery.cancelled";
            };
            String expectedSourceType = switch (contribution.metric()) {
                case COMPLETED_VISITS -> "MEDICAL_RECORD";
                case ADMISSIONS, DISCHARGES -> "ADMISSION";
                case LAB_TESTS -> "LAB_RESULT";
                case DISPENSED_PRESCRIPTIONS, DISPENSED_UNITS -> "DISPENSE";
                case SURGERIES_COMPLETED, SURGERIES_CANCELLED, SURGERY_DURATION_MINUTES -> "SURGERY_OUTCOME";
            };
            if (!expectedEventType.equals(event.metadata().eventType())
                    || !expectedSourceType.equals(contribution.sourceType())) {
                throw new IllegalArgumentException("Metric does not match the source event/type namespace");
            }
        }
    }
}
