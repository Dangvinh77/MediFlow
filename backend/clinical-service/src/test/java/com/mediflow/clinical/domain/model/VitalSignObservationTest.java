package com.mediflow.clinical.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VitalSignObservationTest {
    private static VitalSignObservation record(Integer pulse, Integer systolic, Integer diastolic, String temperature, Integer breaths, Integer spo2,
            String weight, String reason) {
        return VitalSignObservation.record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-11-10T01:00:00Z"),
                UUID.randomUUID(), UUID.randomUUID(), pulse, systolic, diastolic, temperature == null ? null : new BigDecimal(temperature), breaths, spo2,
                weight == null ? null : new BigDecimal(weight), reason);
    }

    private static void rejected(String code, Runnable command) {
        assertThatThrownBy(command::run).isInstanceOf(IllegalArgumentException.class).hasMessage(code);
    }

    @Test void acceptsFullMeasurement() {
        var observation = record(80, 120, 80, "37.0", 18, 98, "60.50", null);
        assertThat(observation.pulseBpm()).isEqualTo(80);
        assertThat(observation.notMeasuredReason()).isNull();
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "19,120,80,37.0,18,98,60", "251,120,80,37.0,18,98,60",
            "80,49,40,37.0,18,98,60", "80,301,80,37.0,18,98,60",
            "80,120,19,37.0,18,98,60", "80,250,201,37.0,18,98,60",
            "80,120,80,29.9,18,98,60", "80,120,80,45.1,18,98,60",
            "80,120,80,37.0,4,98,60", "80,120,80,37.0,81,98,60",
            "80,120,80,37.0,18,49,60", "80,120,80,37.0,18,101,60",
            "80,120,80,37.0,18,98,0.4", "80,120,80,37.0,18,98,400.1",
            "80,120,80,37.05,18,98,60", "80,120,80,37.0,18,98,60.123"})
    void rejectsOutOfRange(Integer pulse, Integer systolic, Integer diastolic, String temperature, Integer breaths, Integer spo2, String weight) {
        rejected("VITAL_SIGNS_INVALID", () -> record(pulse, systolic, diastolic, temperature, breaths, spo2, weight, null));
    }

    @ParameterizedTest
    @CsvSource({"20,50,20,30.0,5,50,0.5", "250,300,200,45.0,80,100,400"})
    void acceptsTheBoundaries(Integer pulse, Integer systolic, Integer diastolic, String temperature, Integer breaths, Integer spo2, String weight) {
        assertThat(record(pulse, systolic, diastolic, temperature, breaths, spo2, weight, null)).isNotNull();
    }

    @Test void bloodPressureNeedsBothValues() {
        rejected("VITAL_SIGNS_INVALID", () -> record(80, 120, null, "37.0", 18, 98, "60", "Không đo được"));
        rejected("VITAL_SIGNS_INVALID", () -> record(80, null, 80, "37.0", 18, 98, "60", "Không đo được"));
    }

    @Test void diastolicMustBeBelowSystolic() {
        rejected("VITAL_SIGNS_INVALID", () -> record(80, 100, 100, "37.0", 18, 98, "60", null));
    }

    @Test void missingMeasurementNeedsReason() {
        rejected("VITAL_SIGNS_REASON_REQUIRED", () -> record(80, 120, 80, "37.0", 18, null, "60", null));
        rejected("VITAL_SIGNS_REASON_REQUIRED", () -> record(80, 120, 80, "37.0", 18, null, "60", "  "));
        assertThat(record(80, 120, 80, "37.0", 18, null, "60", "Máy SpO2 hỏng").notMeasuredReason()).isEqualTo("Máy SpO2 hỏng");
    }

    @Test void allMissingStillNeedsReason() {
        rejected("VITAL_SIGNS_REASON_REQUIRED", () -> record(null, null, null, null, null, null, null, null));
        assertThat(record(null, null, null, null, null, null, null, "Bệnh nhân từ chối đo")).isNotNull();
    }
}
