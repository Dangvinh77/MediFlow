package com.mediflow.clinical.application.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.Size;

public record VitalSignCreateRequest(Integer pulseBpm, Integer systolicMmhg, Integer diastolicMmhg, BigDecimal temperatureC,
        Integer respiratoryRate, Integer spo2Percent, BigDecimal weightKg, @Size(max = 500) String notMeasuredReason) {}
