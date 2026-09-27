package com.mediflow.clinical.infrastructure.client;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.port.out.ExamPricePolicyPort;
import com.mediflow.clinical.infrastructure.config.ClinicalExamPriceProperties;

@Component
public class ConfiguredExamPricePolicyAdapter implements ExamPricePolicyPort {
    private final ClinicalExamPriceProperties properties;

    public ConfiguredExamPricePolicyAdapter(ClinicalExamPriceProperties properties) {
        this.properties = properties;
    }

    @Override
    public String resolvePriceCode(UUID departmentId) {
        if (departmentId == null) {
            throw new IllegalArgumentException("Department id is required to resolve exam price code");
        }
        String code = properties.getByDepartment().getOrDefault(departmentId, properties.getDefaultCode());
        if (code == null || code.isBlank()) {
            throw new IllegalStateException("Configured exam price code is required");
        }
        return code;
    }
}
