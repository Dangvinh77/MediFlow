package com.mediflow.pharmacy.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.application.mapper.DrugDtoMapper;
import com.mediflow.pharmacy.application.port.out.PharmacyEventPublisherPort;
import com.mediflow.pharmacy.application.port.out.StockAdjustmentRepositoryPort;
import com.mediflow.pharmacy.application.port.out.StockReservationRepositoryPort;
import com.mediflow.pharmacy.application.service.DrugApplicationService;
import com.mediflow.pharmacy.infrastructure.persistence.adapter.DrugPersistenceAdapter;
import com.mediflow.pharmacy.infrastructure.persistence.repository.DrugJpaEntityRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Actual JPA drug creation and PostgreSQL receipt/lock/rollback behavior, without a broker. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class LocalDemoDrugSeederPostgresTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DrugJpaEntityRepository repository;
    private final ObjectMapper mapper = new ObjectMapper();
    private JsonNode catalogue;
    private LocalDemoDrugSeeder seeder;

    @BeforeEach
    void setup() throws Exception {
        jdbc.execute("TRUNCATE TABLE local_demo_drug_seed_receipt, drug CASCADE");
        try (var input = new ClassPathResource("demo-drugs.json").getInputStream()) {
            catalogue = mapper.readTree(input);
        }
        seeder = seederAt("2026-10-25T00:00:00Z");
    }

    private LocalDemoDrugSeeder seederAt(String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        var drugs = new DrugApplicationService(new DrugPersistenceAdapter(repository),
                mock(StockReservationRepositoryPort.class), mock(PharmacyEventPublisherPort.class),
                Mappers.getMapper(DrugDtoMapper.class), clock, mock(StockAdjustmentRepositoryPort.class));
        return new LocalDemoDrugSeeder(jdbc, new TransactionTemplate(transactions), drugs, clock);
    }

    @Test
    void firstStartupCreatesValidatedCatalogueAndRestartPreservesExactRows() {
        assertThat(seeder.seed(catalogue)).isEqualTo(12);
        var before = jdbc.queryForList("SELECT * FROM drug ORDER BY drug_id");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM local_demo_drug_seed_receipt", Long.class)).isEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM drug WHERE expiry_date='2029-10-25'", Long.class)).isEqualTo(12);
        assertThat(seederAt("2030-01-01T00:00:00Z").seed(catalogue)).isZero();
        assertThat(jdbc.queryForList("SELECT * FROM drug ORDER BY drug_id")).isEqualTo(before);
    }

    @Test
    void restartNeverResetsEditedNamePriceStockOrExpiryOrRecreatesDeletedDrug() {
        seeder.seed(catalogue);
        jdbc.update("UPDATE drug SET drug_name='Edited', price=9999, stock_quantity=3, expiry_date='2027-01-01' "
                + "WHERE drug_id=(SELECT drug_id FROM local_demo_drug_seed_receipt WHERE seed_key='drug.paracetamol-500')");
        jdbc.update("DELETE FROM drug WHERE drug_id=(SELECT drug_id FROM local_demo_drug_seed_receipt WHERE seed_key='drug.zinc-10')");
        var before = jdbc.queryForList("SELECT * FROM drug ORDER BY drug_id");
        assertThat(seeder.seed(catalogue)).isZero();
        assertThat(jdbc.queryForList("SELECT * FROM drug ORDER BY drug_id")).isEqualTo(before);
        assertThat(before).hasSize(11);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM local_demo_drug_seed_receipt", Long.class)).isEqualTo(12);
    }

    @Test
    void invalidLaterDrugRollsBackBothEarlierDrugAndReceiptAndCanRetry() {
        var invalid = catalogue.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalid.get(1)).put("stockQuantity", -1);
        assertThatThrownBy(() -> seeder.seed(invalid)).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM drug", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM local_demo_drug_seed_receipt", Long.class)).isZero();
        assertThat(seeder.seed(catalogue)).isEqualTo(12);
    }

    @Test
    void duplicateSeedKeyRollsBackInsteadOfSilentlyChangingCatalogue() {
        var invalid = catalogue.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalid.get(1)).put("key", invalid.get(0).get("key").asText());
        assertThatThrownBy(() -> seeder.seed(invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM drug", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM local_demo_drug_seed_receipt", Long.class)).isZero();
    }

    @Test
    void concurrentStartupCreatesOnlyOneCatalogue() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> calls = List.of(() -> seeder.seed(catalogue), () -> seeder.seed(catalogue));
            var results = pool.invokeAll(calls, 30, TimeUnit.SECONDS);
            assertThat(results.get(0).get() + results.get(1).get()).isEqualTo(12);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM drug", Long.class)).isEqualTo(12);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM local_demo_drug_seed_receipt", Long.class)).isEqualTo(12);
        } finally {
            pool.shutdownNow();
        }
    }
}
