package com.mediflow.inpatient.infrastructure.config;

import com.mediflow.inpatient.application.dto.event.DepositSuggestion;
import com.mediflow.inpatient.domain.model.enums.AdmissionPriority;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mediflow.inpatient.deposit-suggestions")
public class DepositSuggestionProperties {
    private Map<AdmissionPriority, DepositSuggestion> byPriority = new EnumMap<>(AdmissionPriority.class);

    public Map<AdmissionPriority, DepositSuggestion> getByPriority() {
        return byPriority;
    }

    public void setByPriority(Map<AdmissionPriority, DepositSuggestion> byPriority) {
        this.byPriority = byPriority == null
                ? new EnumMap<>(AdmissionPriority.class)
                : new EnumMap<>(byPriority);
    }
}
