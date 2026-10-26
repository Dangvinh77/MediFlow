package com.mediflow.pharmacy.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.application.port.in.ManageDrugUseCase;
import java.time.Clock;
import javax.sql.DataSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit development opt-in only; never changes existing stock or demo expiry dates. */
@Configuration(proxyBeanMethods = false)
@Profile("!prod & !production & !test & !acceptancefixture")
@ConditionalOnProperty(name = "mediflow.pharmacy.demo-seed.enabled", havingValue = "true")
public class LocalDemoDrugSeedConfiguration {
    @Bean
    LocalDemoDrugSeeder localDemoDrugSeeder(DataSource source, PlatformTransactionManager transactions,
            ManageDrugUseCase drugs, Clock clock) {
        return new LocalDemoDrugSeeder(new JdbcTemplate(source), new TransactionTemplate(transactions), drugs, clock);
    }

    @Bean
    ApplicationRunner localDemoDrugSeedRunner(ObjectMapper mapper, LocalDemoDrugSeeder seeder) {
        return args -> {
            try (var input = new ClassPathResource("demo-drugs.json").getInputStream()) {
                int inserted = seeder.seed(mapper.readTree(input));
                LoggerFactory.getLogger(LocalDemoDrugSeedConfiguration.class)
                        .info("Local demo drug catalogue ready: {} inserted; existing edits and stock preserved", inserted);
            }
        };
    }
}
