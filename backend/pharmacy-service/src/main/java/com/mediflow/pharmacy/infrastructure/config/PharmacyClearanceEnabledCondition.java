package com.mediflow.pharmacy.infrastructure.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class PharmacyClearanceEnabledCondition implements Condition {
    @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var environment = context.getEnvironment();
        return environment.getProperty("mediflow.features.care-finance-v2", Boolean.class, false)
                && environment.getProperty("mediflow.pharmacy.clearance-consumer.enabled", Boolean.class, false);
    }
}
