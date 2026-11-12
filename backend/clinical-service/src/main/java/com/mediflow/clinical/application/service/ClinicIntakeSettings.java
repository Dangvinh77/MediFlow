package com.mediflow.clinical.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Whether the department-desk intake rules of v2.0 wave 2 apply. Off keeps every existing behavior. */
@Component
public class ClinicIntakeSettings {
    private final boolean enabled;

    public ClinicIntakeSettings(@Value("${mediflow.v2.clinic-intake:false}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }
}
