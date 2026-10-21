package com.mediflow.acceptance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;

/** Test-classpath only. Advances Report's application clock, never JWT/system/source clocks. */
public final class ReportCalendarAcceptanceInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        String location = System.getenv("MEDIFLOW_FE_REPORT_TIME_FILE");
        if (location == null) throw new IllegalStateException("Explicit isolated calendar-test file required");
        Path file = Path.of(location);
        Clock clock = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() {
                try {
                    if (!Files.exists(file)) return Instant.now();
                    Instant advanced = Instant.parse(Files.readString(file).trim());
                    if (advanced.isBefore(Instant.now())) throw new IllegalStateException("Test clock may only advance");
                    return advanced;
                } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
            }
        };
        ((BeanDefinitionRegistry) context.getBeanFactory()).registerBeanDefinition("reportClock",
                new RootBeanDefinition(Clock.class, () -> clock));
    }
}
