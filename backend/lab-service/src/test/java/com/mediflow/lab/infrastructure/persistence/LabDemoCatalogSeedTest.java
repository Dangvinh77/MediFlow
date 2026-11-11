package com.mediflow.lab.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The demo catalogue only names price codes the Billing demo configuration can price; a code Billing
 * does not know dead-letters the charge. Reads both files from the source tree, never across services.
 */
class LabDemoCatalogSeedTest {
    private static final Path SEED = Path.of("src/main/resources/db/migration/V7__demo_lab_catalog.sql");
    private static final Path BILLING = Path.of("../billing-service/src/main/resources/application.yml");

    @Test
    void everyCataloguePriceCodeIsPricedByTheBillingDemo() throws Exception {
        String seed = Files.readString(SEED, StandardCharsets.UTF_8);
        Set<String> codes = new LinkedHashSet<>();
        Matcher m = Pattern.compile("'(LAB-[A-Z0-9-]+)'").matcher(seed);
        while (m.find()) {
            codes.add(m.group(1));
        }
        assertThat(codes).containsExactlyInAnyOrder("LAB-CBC", "LAB-GLU", "LAB-HBSAG", "LAB-UA", "LAB-MICRO");

        String billing = Files.readString(BILLING, StandardCharsets.UTF_8);
        for (String code : codes) {
            assertThat(billing).as("Billing demo price for " + code).containsPattern("(?m)^\\s+" + Pattern.quote(code) + ":");
        }
    }

    @Test
    void demoRangesAreMarkedAsNotClinicallyReviewed() throws Exception {
        String seed = Files.readString(SEED, StandardCharsets.UTF_8);
        var rangeRows = seed.lines().filter(line -> line.contains("gen_random_uuid()")).toList();
        assertThat(rangeRows).isNotEmpty().allSatisfy(row -> assertThat(row.strip()).matches(".*, false\\)[,;]"));
    }
}
