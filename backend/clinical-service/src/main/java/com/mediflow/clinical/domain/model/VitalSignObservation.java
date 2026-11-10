package com.mediflow.clinical.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One measuring round, never edited. The limits only catch typing mistakes; the system does not judge
 * whether a value is clinically abnormal.
 */
public record VitalSignObservation(UUID observationId, UUID registrationId, UUID patientId, UUID departmentId, Instant observedAt,
        UUID recordedByAccountId, UUID recordedByStaffId, Integer pulseBpm, Integer systolicMmhg, Integer diastolicMmhg,
        BigDecimal temperatureC, Integer respiratoryRate, Integer spo2Percent, BigDecimal weightKg, String notMeasuredReason) {
    private static final String INVALID = "VITAL_SIGNS_INVALID";
    private static final String REASON_REQUIRED = "VITAL_SIGNS_REASON_REQUIRED";

    public static VitalSignObservation record(UUID observationId, UUID registrationId, UUID patientId, UUID departmentId, Instant observedAt,
            UUID recordedByAccountId, UUID recordedByStaffId, Integer pulseBpm, Integer systolicMmhg, Integer diastolicMmhg,
            BigDecimal temperatureC, Integer respiratoryRate, Integer spo2Percent, BigDecimal weightKg, String notMeasuredReason) {
        range(pulseBpm, 20, 250);
        range(systolicMmhg, 50, 300);
        range(diastolicMmhg, 20, 200);
        range(respiratoryRate, 5, 80);
        range(spo2Percent, 50, 100);
        range(temperatureC, "30.0", "45.0", 1);
        range(weightKg, "0.5", "400", 2);
        if ((systolicMmhg == null) != (diastolicMmhg == null)) throw new IllegalArgumentException(INVALID);
        if (systolicMmhg != null && diastolicMmhg >= systolicMmhg) throw new IllegalArgumentException(INVALID);
        boolean incomplete = pulseBpm == null || systolicMmhg == null || temperatureC == null || respiratoryRate == null
                || spo2Percent == null || weightKg == null;
        boolean hasReason = notMeasuredReason != null && !notMeasuredReason.isBlank();
        if (incomplete && !hasReason) throw new IllegalArgumentException(REASON_REQUIRED);
        if (notMeasuredReason != null && notMeasuredReason.length() > 500) throw new IllegalArgumentException(INVALID);
        return new VitalSignObservation(observationId, registrationId, patientId, departmentId, observedAt, recordedByAccountId,
                recordedByStaffId, pulseBpm, systolicMmhg, diastolicMmhg, temperatureC, respiratoryRate, spo2Percent, weightKg,
                hasReason ? notMeasuredReason : null);
    }

    private static void range(Integer value, int min, int max) {
        if (value != null && (value < min || value > max)) throw new IllegalArgumentException(INVALID);
    }

    private static void range(BigDecimal value, String min, String max, int scale) {
        if (value == null) return;
        if (value.compareTo(new BigDecimal(min)) < 0 || value.compareTo(new BigDecimal(max)) > 0
                || value.stripTrailingZeros().scale() > scale) {
            throw new IllegalArgumentException(INVALID);
        }
    }
}
