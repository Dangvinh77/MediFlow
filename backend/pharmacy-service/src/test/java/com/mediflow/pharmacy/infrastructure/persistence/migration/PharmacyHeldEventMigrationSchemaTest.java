package com.mediflow.pharmacy.infrastructure.persistence.migration;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PharmacyHeldEventMigrationSchemaTest {
    @Test
    void v17KeepsLegacyDefaultsAndRequiresExplicitMigrationForV1Activation() throws Exception {
        try (var stream = getClass().getResourceAsStream("/db/migration/V17__held_prescription_care_events.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
            assertThat(sql).contains("drug_name_snapshot", "lifecycle_at_iso", "delivery_enabled boolean not null default true",
                    "care_contract_version smallint not null default 0", "and not delivery_enabled", "uq_outbox_care_lifecycle");
            assertThat(sql).doesNotContain("update prescription", "update dispense_slip", "truncate ", "drop table", "delete from");
        }
    }
}
