package com.mediflow.clinical.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class ClinicalArchitectureTest {

    @Test
    void domainSourcesDoNotDependOnApplicationOrInfrastructure() throws IOException {
        Path domainSources = Path.of(System.getProperty("user.dir"), "src", "main", "java",
                "com", "mediflow", "clinical", "domain");
        List<String> violations;
        try (Stream<Path> paths = Files.walk(domainSources)) {
            violations = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> {
                        try {
                            return Files.readAllLines(path).stream();
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .filter(line -> line.startsWith("import "))
                    .filter(line -> !line.startsWith("import java.")
                            && !line.startsWith("import lombok.")
                            && !line.startsWith("import com.mediflow.common.")
                            && !line.startsWith("import com.mediflow.clinical.domain."))
                    .toList();
        }

        assertThat(violations).isEmpty();
    }
}
