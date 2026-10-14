package com.mediflow.clinical.infrastructure.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Enables the one Clinical outbox worker when either producer mode can create durable rows. */
public final class ClinicalOutboxActivationCondition implements Condition {

    private static final String CARE_FINANCE_V2 = "mediflow.features.care-finance-v2.enabled";
    private static final String DURABLE_EVENTS = "mediflow.clinical.durable-events.enabled";
    private static final String OUTPATIENT_MVP = "mediflow.features.outpatient-mvp.enabled";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var environment = context.getEnvironment();
        return environment.getProperty(CARE_FINANCE_V2, Boolean.class, false)
                || environment.getProperty(DURABLE_EVENTS, Boolean.class, false)
                || environment.getProperty(OUTPATIENT_MVP, Boolean.class, false)
                || environment.acceptsProfiles(Profiles.of("outpatient-mvp"));
    }
}
