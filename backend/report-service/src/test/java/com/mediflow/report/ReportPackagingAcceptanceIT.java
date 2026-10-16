package com.mediflow.report;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

/** Artifact inspection after package, not proof that Support host or PostgreSQL startup passed. */
class ReportPackagingAcceptanceIT {
    private final String artifact="report-service-0.0.1-SNAPSHOT";
    @Test void plainLibraryHasRootClassesNotBootInfAndKeepsMigrationBytes()throws Exception{
        try(var jar=new JarFile(Path.of("target",artifact+"-lib.jar").toFile())){
            assertThat(jar.getJarEntry("com/mediflow/report/application/service/ReportApplicationService.class")).isNotNull();
            assertThat(jar.getJarEntry("BOOT-INF/classes/")).isNull();
            assertThat(jar.getJarEntry("db/migration/V1__init.sql")).isNotNull();
            assertThat(jar.getJarEntry("db/migration/V27__outpatient_read_coverage.sql")).isNotNull();
            java.util.List<String> migrations;
            try (var files = Files.list(Path.of("src/main/resources/db/migration"))) {
                migrations = files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".sql")).sorted().toList();
            }
            // Version 27 is the latest version, not a count: released V3–V5 do not exist here.
            assertThat(migrations).hasSize(24).contains("V1__init.sql", "V27__outpatient_read_coverage.sql");
            for (String migration : migrations) {
                try (var bytes = jar.getInputStream(jar.getJarEntry("db/migration/" + migration))) {
                    assertThat(bytes.readAllBytes()).isEqualTo(Files.readAllBytes(
                            Path.of("src/main/resources/db/migration", migration)));
                }
                try (var bytes = jar.getInputStream(jar.getJarEntry("mediflow/report/db/migration/" + migration))) {
                    assertThat(bytes.readAllBytes()).isEqualTo(Files.readAllBytes(
                            Path.of("src/main/resources/db/migration", migration)));
                }
            }
        }
    }
    @Test void mainArtifactKeepsStandaloneRollbackLauncher()throws Exception{
        try(var jar=new JarFile(Path.of("target",artifact+".jar").toFile())){
            assertThat(jar.getJarEntry("BOOT-INF/classes/com/mediflow/report/ReportServiceApplication.class")).isNotNull();
            assertThat(jar.getManifest().getMainAttributes().getValue("Start-Class"))
                    .isEqualTo("com.mediflow.report.ReportServiceApplication");
        }
    }
}
