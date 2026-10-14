package com.mediflow;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/** Minimal root configuration for legacy MVC and persistence slice tests. */
@SpringBootApplication(scanBasePackages = {"com.mediflow.organization", "com.mediflow.patient"})
@Import({
        com.mediflow.organization.infrastructure.correlation.ThreadLocalCorrelationIdProvider.class,
        com.mediflow.patient.infrastructure.correlation.ThreadLocalCorrelationIdProvider.class
})
public class TestSliceApplication {
}
