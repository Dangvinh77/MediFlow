package com.mediflow.surgery.infrastructure.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Requires both independent rollout switches before creating any broker publisher/scheduler. */
public class SurgeryOutboxEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return context.getEnvironment().getProperty("mediflow.features.surgery.enabled", Boolean.class, false)
                        && context.getEnvironment().getProperty(
                                "mediflow.surgery.messaging.producer.enabled", Boolean.class, false);
    }
}
