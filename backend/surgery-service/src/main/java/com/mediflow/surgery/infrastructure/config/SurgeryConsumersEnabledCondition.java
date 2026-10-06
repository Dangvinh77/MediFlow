package com.mediflow.surgery.infrastructure.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class SurgeryConsumersEnabledCondition implements Condition {
    @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return context.getEnvironment().getProperty("mediflow.features.surgery.enabled",Boolean.class,false)
                && context.getEnvironment().getProperty("mediflow.surgery.messaging.consumers.enabled",Boolean.class,false);
    }
}
