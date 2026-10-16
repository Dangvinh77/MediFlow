package com.mediflow.report;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Real JVM/classpath proof only; not PostgreSQL, RabbitMQ or Support-host acceptance. */
class ReportPackagedModuleAcceptanceIT {
    @TempDir Path isolated;

    @ParameterizedTest
    @ValueSource(strings = {"reader", "wrong-mode", "foreign-default-store"})
    void plainJarLoadsScopedReaderAndRejectsUnsafeComposition(String scenario) throws Exception {
        Path library = Path.of("target/report-service-0.0.1-SNAPSHOT-lib.jar").toRealPath();
        Path mainClasses = Path.of("target/classes").toAbsolutePath().normalize();
        Path testClasses = Path.of("target/test-classes").toAbsolutePath().normalize();
        // Copy only this shim, not test configuration/controller classes from the module's test tree.
        for (String name : java.util.List.of("ReportLibraryProbe", "ReportLibraryProbe$BindingConfiguration")) {
            String resource = "/com/mediflow/packaging/" + name + ".class";
            Path target = isolated.resolve(resource.substring(1));
            Files.createDirectories(target.getParent());
            try (var input = getClass().getResourceAsStream(resource)) {
                assertThat(input).as(resource).isNotNull();
                Files.copy(input, target);
            }
        }
        String dependencies = System.getProperty("surefire.test.class.path");
        assertThat(dependencies).as("Failsafe must supply its explicit dependency classpath").isNotBlank();
        var classpath = new ArrayList<String>();
        classpath.add(isolated.toAbsolutePath().toString());
        classpath.add(library.toString());
        Arrays.stream(dependencies.split(Pattern.quote(File.pathSeparator))).map(Path::of)
                .map(p -> p.toAbsolutePath().normalize())
                .filter(p -> !p.equals(mainClasses) && !p.equals(testClasses))
                .filter(p -> !p.getFileName().toString().startsWith("report-service-"))
                .forEach(p -> classpath.add(p.toString()));
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java" + (File.separatorChar == '\\' ? ".exe" : "")).toString();
        Process process = new ProcessBuilder(javaExecutable, "-cp", String.join(File.pathSeparator, classpath),
                "com.mediflow.packaging.ReportLibraryProbe", scenario, library.toString()).redirectErrorStream(true).start();
        try {
            boolean completed = process.waitFor(30, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new AssertionError("Packaged Report probe exceeded its bounded startup time");
            }
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(process.exitValue()).as(output).isZero();
            assertThat(output).contains("REPORT_LIBRARY_PROBE_OK:" + scenario);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
