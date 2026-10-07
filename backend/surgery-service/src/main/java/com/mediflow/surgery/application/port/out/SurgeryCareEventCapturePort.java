package com.mediflow.surgery.application.port.out;

import com.mediflow.surgery.application.event.SurgeryCareEvent;

/** Capture approved wire atomically, without releasing it to consumers before cutover acceptance. */
public interface SurgeryCareEventCapturePort {
    void hold(SurgeryCareEvent event, long caseRevision);
}
