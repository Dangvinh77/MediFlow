package com.mediflow.pharmacy.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LocalDemoDrugSeedConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LocalDemoDrugSeedConfiguration.class);

    @Test
    void seedingIsDisabledWithoutExplicitOptIn() {
        runner.run(context -> assertThat(context).doesNotHaveBean(LocalDemoDrugSeeder.class));
    }

    @Test
    void productionAndTestProfilesExcludeSeederEvenWhenOptedIn() {
        for (String profile : new String[]{"prod", "production", "test", "acceptancefixture"}) {
            runner.withPropertyValues("spring.profiles.active=" + profile,
                    "mediflow.pharmacy.demo-seed.enabled=true")
                    .run(context -> assertThat(context).doesNotHaveBean(LocalDemoDrugSeeder.class));
        }
    }
}
