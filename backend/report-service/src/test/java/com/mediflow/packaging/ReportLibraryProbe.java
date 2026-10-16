package com.mediflow.packaging;

import com.mediflow.report.application.exception.ReportProjectionUnavailableException;
import com.mediflow.report.application.port.in.ReadOutpatientReportUseCase;
import com.mediflow.report.application.port.out.OutpatientReportReadPort;
import com.mediflow.report.infrastructure.config.ReportModuleConfiguration;
import com.mediflow.report.infrastructure.config.ReportModulePersistenceConfiguration;
import com.mediflow.report.infrastructure.config.ReportOutpatientReadConfiguration;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;

/** Forked test shim: Report production classes must load from the plain artifact, never target/classes. */
public final class ReportLibraryProbe {
    public static void main(String[] args) throws Exception {
        Path expected = Path.of(args[1]).toRealPath();
        Path origin = Path.of(ReportModuleConfiguration.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
        require(origin.equals(expected), "Report did not load from the selected plain JAR");
        switch (args[0]) {
            case "reader" -> reader();
            case "wrong-mode" -> rejected(false);
            case "foreign-default-store" -> rejected(true);
            default -> throw new IllegalArgumentException("Unknown packaged probe");
        }
        System.out.println("REPORT_LIBRARY_PROBE_OK:" + args[0]);
    }

    private static void reader() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("report-module", "outpatient-mvp");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("isolated-test", Map.of(
                    "mediflow.features.care-finance-v2", "true", "mediflow.report.outpatient-read.enabled", "true")));
            context.registerBean("reportZoneId", ZoneId.class, () -> ZoneId.of("Asia/Bangkok"));
            context.registerBean("foreignZoneId", ZoneId.class, () -> ZoneId.of("UTC"));
            context.registerBean(OutpatientReportReadPort.class, () -> (OutpatientReportReadPort) Proxy.newProxyInstance(
                    ReportLibraryProbe.class.getClassLoader(), new Class<?>[]{OutpatientReportReadPort.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("load")) {
                            require(arguments[3].equals("Asia/Bangkok"), "Reader used another module's zone");
                            return Optional.empty();
                        }
                        if (method.getName().equals("toString")) return "isolated-report-read-port";
                        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                        return proxy == arguments[0];
                    }));
            context.register(ReportOutpatientReadConfiguration.class);
            context.refresh();
            try {
                context.getBean(ReadOutpatientReportUseCase.class)
                        .daily(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1), null);
                throw new AssertionError("Missing coverage became a zero report");
            } catch (ReportProjectionUnavailableException expected) {
                require("REPORT_NOT_FOUND".equals(expected.getCode()), "Unexpected unavailable contract");
            }
        }
    }

    private static void rejected(boolean foreignStore) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(foreignStore
                    ? new String[]{"report-module", "outpatient-mvp"} : new String[]{"report-module"});
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("isolated-test", Map.of(
                    "spring.datasource.url", "jdbc:postgresql://127.0.0.1:1/foreign_test_only",
                    "spring.datasource.username", "foreign_test_only", "spring.datasource.password", "foreign_test_only")));
            context.register(foreignStore ? BindingConfiguration.class : ReportModuleConfiguration.class);
            String reason = foreignStore ? "Explicit Report PostgreSQL credentials" : "requires an explicit outpatient MVP profile";
            try {
                context.refresh();
                throw new AssertionError("Unsafe module configuration was accepted");
            } catch (RuntimeException failure) {
                for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                    if (cause.getMessage() != null && cause.getMessage().contains(reason)) return;
                }
                throw new AssertionError("Module failed for a different reason than its safety guard", failure);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties
    @Import(ReportModulePersistenceConfiguration.class)
    static class BindingConfiguration { }
}
