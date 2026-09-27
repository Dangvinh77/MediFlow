package com.mediflow.clinical.infrastructure.config;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mediflow.clinical.exam-price")
public class ClinicalExamPriceProperties {
    private String defaultCode = "OUTPATIENT_EXAM";
    private Map<UUID, String> byDepartment = new HashMap<>();

    public String getDefaultCode() {
        return defaultCode;
    }

    public void setDefaultCode(String defaultCode) {
        this.defaultCode = defaultCode;
    }

    public Map<UUID, String> getByDepartment() {
        return byDepartment;
    }

    public void setByDepartment(Map<UUID, String> byDepartment) {
        this.byDepartment = byDepartment == null ? new HashMap<>() : new HashMap<>(byDepartment);
    }
}
