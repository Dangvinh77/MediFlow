package com.mediflow.surgery;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureTest {

    private static final Path JAVA_ROOT = Path.of("src/main/java/com/mediflow/surgery");

    @Test
    void domain_doesNotImportFrameworkOrIoTypes() throws IOException {
        assertNoImports("domain", List.of(
                "org.springframework.",
                "jakarta.persistence.",
                "jakarta.validation.",
                "jakarta.servlet.",
                "org.springframework.amqp.",
                "java.io."));
    }

    @Test
    void application_doesNotImportOutboundAdapterFrameworks() throws IOException {
        assertNoImports("application", List.of(
                "org.springframework.data.",
                "jakarta.persistence.",
                "org.springframework.amqp.",
                "org.springframework.web.",
                "com.mediflow.surgery.infrastructure."));
    }

    private void assertNoImports(String layer, List<String> forbiddenImports) throws IOException {
        Path layerRoot = JAVA_ROOT.resolve(layer);
        if (!Files.exists(layerRoot)) {
            return;
        }
        try (Stream<Path> files = Files.walk(layerRoot)) {
            List<String> violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> readLines(path, forbiddenImports).stream())
                    .toList();
            assertThat(violations).as("forbidden imports in %s", layer).isEmpty();
        }
    }

    private List<String> readLines(Path file, List<String> forbiddenImports) {
        try {
            return Files.readAllLines(file).stream()
                    .filter(line -> line.stripLeading().startsWith("import "))
                    .filter(line -> forbiddenImports.stream().anyMatch(line::contains))
                    .map(line -> file + ": " + line.trim())
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not inspect architecture source " + file, exception);
        }
    }
}
