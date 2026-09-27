package com.mediflow.inpatient.infrastructure.persistence.adapter;

import com.mediflow.inpatient.application.dto.event.DepositSuggestion;
import com.mediflow.inpatient.application.port.out.DepositSuggestionPolicyPort;
import com.mediflow.inpatient.domain.exception.AdmissionRuleViolationException;
import com.mediflow.inpatient.domain.model.enums.AdmissionPriority;
import com.mediflow.inpatient.infrastructure.config.DepositSuggestionProperties;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ConfigurableDepositSuggestionPolicy implements DepositSuggestionPolicyPort {
    private final DepositSuggestionProperties properties;

    public ConfigurableDepositSuggestionPolicy(DepositSuggestionProperties properties) {
        this.properties = properties;
    }

    @Override
    public DepositSuggestion suggest(AdmissionPriority priority, UUID departmentId) {
        DepositSuggestion suggestion = properties.getByPriority().get(priority);
        if (suggestion == null) {
            throw new AdmissionRuleViolationException("INPATIENT_DEPOSIT_SUGGESTION_NOT_CONFIGURED",
                    "No deposit suggestion is configured for admission priority " + priority);
        }
        return suggestion;
    }
}
