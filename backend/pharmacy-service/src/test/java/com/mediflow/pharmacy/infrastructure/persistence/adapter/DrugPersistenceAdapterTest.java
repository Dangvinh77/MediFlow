package com.mediflow.pharmacy.infrastructure.persistence.adapter;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.pharmacy.domain.model.Drug;
import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.DrugJpaEntity;
import com.mediflow.pharmacy.infrastructure.persistence.repository.DrugJpaEntityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(DrugPersistenceAdapter.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class DrugPersistenceAdapterTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private DrugPersistenceAdapter adapter;

    @Autowired
    private DrugJpaEntityRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void search_isCaseInsensitiveAndKeepsPageMetadata() {
        adapter.save(newDrug("Paracetamol 500mg"));
        adapter.save(newDrug("Paracetamol 650mg"));
        adapter.save(newDrug("Amoxicillin 500mg"));

        PageResult<Drug> result = adapter.search("PARACETAMOL", new PageQuery(0, 1));

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.number()).isZero();
        assertThat(result.size()).isEqualTo(1);
    }

    @Test
    void search_nullKeywordReturnsAllDrugs() {
        adapter.save(newDrug("Paracetamol 500mg"));
        adapter.save(newDrug("Amoxicillin 500mg"));

        PageResult<Drug> result = adapter.search(null, new PageQuery(0, 20));

        assertThat(result.content()).hasSize(2);
        assertThat(result.totalElements()).isEqualTo(2);
    }

    @Test
    void save_existingDrugPreservesCreatedAt() {
        Drug created = adapter.save(newDrug("Paracetamol 500mg"));
        assertThat(created.getCreatedAt()).isNotNull();

        created.adjustStock(10);
        Drug updated = adapter.save(created);

        assertThat(updated.getCreatedAt()).isEqualTo(created.getCreatedAt());
        assertThat(updated.getStockQuantity()).isEqualTo(110);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void findByIdForUpdate_blocksConcurrentTransactionUntilCommit() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        UUID drugId = transaction.execute(status -> repository.saveAndFlush(drugEntity()).getDrugId());
        assertThat(drugId).isNotNull();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstTransactionLocked = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);
        CompletableFuture<Void> waitingTransaction = null;

        CompletableFuture<Void> lockingTransaction = CompletableFuture.runAsync(() ->
                transaction.executeWithoutResult(status -> {
                    repository.findByIdForUpdate(drugId).orElseThrow();
                    firstTransactionLocked.countDown();
                    await(releaseFirstTransaction);
                }), executor);

        try {
            assertThat(firstTransactionLocked.await(5, TimeUnit.SECONDS)).isTrue();

            waitingTransaction = CompletableFuture.runAsync(() ->
                    transaction.executeWithoutResult(status ->
                            repository.findByIdForUpdate(drugId).orElseThrow()), executor);

            CompletableFuture<Void> finalWaitingTransaction = waitingTransaction;
            assertThatThrownBy(() -> finalWaitingTransaction.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
        } finally {
            releaseFirstTransaction.countDown();
            lockingTransaction.get(5, TimeUnit.SECONDS);
            if (waitingTransaction != null) {
                waitingTransaction.get(5, TimeUnit.SECONDS);
            }
            executor.shutdownNow();
            transaction.executeWithoutResult(status -> repository.deleteById(drugId));
        }
    }

    private Drug newDrug(String name) {
        return Drug.create(
                name,
                "Hoạt chất",
                "viên",
                new BigDecimal("1200.00"),
                100,
                LocalDate.now().plusYears(1),
                "Dược phẩm VN",
                20);
    }

    private DrugJpaEntity drugEntity() {
        return DrugJpaEntity.builder()
                .drugName("Thuốc khóa " + UUID.randomUUID())
                .activeIngredient("Hoạt chất")
                .unit("viên")
                .price(new BigDecimal("1000.00"))
                .stockQuantity(10)
                .expiryDate(LocalDate.now().plusYears(1))
                .manufacturer("Dược phẩm VN")
                .lowStockThreshold(2)
                .build();
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Hết thời gian chờ transaction kiểm thử");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Thread kiểm thử bị gián đoạn", exception);
        }
    }
}
