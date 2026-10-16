package com.mediflow.pharmacy.infrastructure.messaging;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Operator-supplied acceptance reference activates NEW aggregates only. Default remains OFF. */
@Component
public final class OutpatientEventActivation {
    private final boolean enabled;
    private final String reference;
    public OutpatientEventActivation(Environment environment) {
        enabled=environment.getProperty("mediflow.pharmacy.outpatient-events.enabled",Boolean.class,false);
        reference=environment.getProperty("mediflow.pharmacy.outpatient-events.acceptance-reference","").trim();
        if(enabled) {
            boolean ready=environment.matchesProfiles("outpatient-mvp","mvp-outpatient");
            for(String flag:java.util.List.of("mediflow.features.care-finance-v2","mediflow.pharmacy.identity.enabled",
                    "mediflow.pharmacy.clinical-context.enabled","mediflow.pharmacy.clearance-consumer.enabled",
                    "mediflow.pharmacy.clearance-authority.enabled","mediflow.pharmacy.outpatient-api.enabled"))
                ready&=environment.getProperty(flag,Boolean.class,false);
            if(!ready || reference.isEmpty() || reference.length()>255 || reference.chars().anyMatch(Character::isISOControl))
                throw new IllegalStateException("Outpatient event activation requires all MVP gates and a bounded acceptance reference");
        }
    }
    public boolean enabled(){return enabled;}
    public String reference(){return reference;}
}
