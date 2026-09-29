package com.mediflow.surgery.infrastructure.config;

import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public class SystemSurgeryClockAdapter implements SurgeryClockPort {

    private final Clock clock;

    public SystemSurgeryClockAdapter() {
        this(Clock.systemUTC());
    }

    SystemSurgeryClockAdapter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Instant now() {
        return clock.instant();
    }
}
