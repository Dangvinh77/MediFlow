package com.mediflow.clinical.web;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** The deferred route is absent whenever either outpatient-MVP selector is active. */
public final class AdmissionReferralEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var environment = context.getEnvironment();
        boolean careFinance = environment.getProperty(
                "mediflow.features.care-finance-v2.enabled", Boolean.class, false);
        boolean admissionReferrals = environment.getProperty(
                "mediflow.features.admission-referral.enabled", Boolean.class, false);
        boolean outpatientMvp = environment.getProperty(
                "mediflow.features.outpatient-mvp.enabled", Boolean.class, false)
                || environment.acceptsProfiles(Profiles.of("outpatient-mvp"));
        return careFinance && admissionReferrals && !outpatientMvp;
    }
}
