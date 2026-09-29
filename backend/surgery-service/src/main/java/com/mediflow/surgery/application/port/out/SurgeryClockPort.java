package com.mediflow.surgery.application.port.out;

import java.time.Instant;

public interface SurgeryClockPort {

    Instant now();
}
