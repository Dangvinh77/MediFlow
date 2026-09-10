package com.mediflow.billing.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mediflow.billing.domain.model.Fee;
import com.mediflow.billing.domain.model.FeeType;

/**
 * Slice test cho {@link FeePersistenceAdapter}: ánh xạ entity ↔ domain, các truy vấn, và tuyến
 * phòng thủ BR-B7 (unique partial index {@code uq_fee_source}). Flyway chạy {@code V1__init.sql}
 * trên Postgres container thật (docs/ai/09-testing.md — "không mock cái mình không sở hữu").
 */
@DataJpaTest
@Import(FeePersistenceAdapter.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class FeePersistenceAdapterTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private FeePersistenceAdapter adapter;

    private static Fee newFee(UUID patientId, UUID departmentId, UUID sourceRefId, FeeType type, String amount) {
        return Fee.create(patientId, UUID.randomUUID(), departmentId, sourceRefId, type,
                LocalDate.of(2026, 9, 8), new BigDecimal(amount));
    }

    @Test
    void save_thenFindById_roundTripsAllFieldsAndFillsTimestamps() {
        UUID patient = UUID.randomUUID();
        UUID dept = UUID.randomUUID();
        UUID source = UUID.randomUUID();

        Fee saved = adapter.save(newFee(patient, dept, source, FeeType.LAB, "120000.00"));

        assertThat(saved.getFeeId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        Fee reloaded = adapter.findById(saved.getFeeId()).orElseThrow();
        assertThat(reloaded.getPatientId()).isEqualTo(patient);
        assertThat(reloaded.getDepartmentId()).isEqualTo(dept);
        assertThat(reloaded.getSourceRefId()).isEqualTo(source);
        assertThat(reloaded.getFeeType()).isEqualTo(FeeType.LAB);
        assertThat(reloaded.getAmount()).isEqualByComparingTo("120000.00");
        assertThat(reloaded.isPaid()).isFalse();
        assertThat(reloaded.getIncurredDate()).isEqualTo(LocalDate.of(2026, 9, 8));
    }

    @Test
    void save_existingFee_preservesCreatedAtAndPersistsPaidFlag() {
        Fee created = adapter.save(newFee(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                FeeType.EXAM, "150000.00"));

        created.markPaid();
        Fee updated = adapter.save(created);

        assertThat(updated.getCreatedAt()).isEqualTo(created.getCreatedAt());
        assertThat(adapter.findById(created.getFeeId()).orElseThrow().isPaid()).isTrue();
    }

    @Test
    void findUnpaidByPatient_returnsOnlyUnpaidOfThatPatient() {
        UUID patient = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID dept = UUID.randomUUID();
        adapter.save(newFee(patient, dept, UUID.randomUUID(), FeeType.EXAM, "100000.00"));
        Fee paid = adapter.save(newFee(patient, dept, UUID.randomUUID(), FeeType.LAB, "50000.00"));
        paid.markPaid();
        adapter.save(paid);
        adapter.save(newFee(other, dept, UUID.randomUUID(), FeeType.EXAM, "999.00"));

        List<Fee> unpaid = adapter.findUnpaidByPatient(patient);

        assertThat(unpaid).singleElement()
                .satisfies(f -> assertThat(f.getAmount()).isEqualByComparingTo("100000.00"));
    }

    @Test
    void saveAll_marksWholeBatchPaid_andFindByInvoiceReadsThemBack() {
        UUID invoiceId = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        UUID dept = UUID.randomUUID();
        Fee a = adapter.save(newFee(patient, dept, UUID.randomUUID(), FeeType.EXAM, "100000.00"));
        Fee b = adapter.save(newFee(patient, dept, UUID.randomUUID(), FeeType.LAB, "60000.00"));
        a.assignToInvoice(invoiceId);
        b.assignToInvoice(invoiceId);
        a.markPaid();
        b.markPaid();

        adapter.saveAll(List.of(a, b));

        List<Fee> inInvoice = adapter.findByInvoice(invoiceId);
        assertThat(inInvoice).hasSize(2).allSatisfy(f -> assertThat(f.isPaid()).isTrue());
    }

    @Test
    void existsBySource_trueOnlyAfterAFeeWithThatTypeAndSourceIsSaved() {
        UUID source = UUID.randomUUID();
        assertThat(adapter.existsBySource(FeeType.LAB, source)).isFalse();

        adapter.save(newFee(UUID.randomUUID(), UUID.randomUUID(), source, FeeType.LAB, "80000.00"));

        assertThat(adapter.existsBySource(FeeType.LAB, source)).isTrue();
        assertThat(adapter.existsBySource(FeeType.EXAM, source)).isFalse();   // khác feeType
    }

    @Test
    void save_secondFeeWithSameTypeAndSource_violatesUniquePartialIndex() {
        UUID source = UUID.randomUUID();
        adapter.save(newFee(UUID.randomUUID(), UUID.randomUUID(), source, FeeType.LAB, "80000.00"));

        assertThatThrownBy(() -> adapter.save(
                newFee(UUID.randomUUID(), UUID.randomUUID(), source, FeeType.LAB, "80000.00")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_multipleFeesWithNullSource_isAllowedByThePartialIndex() {
        UUID patient = UUID.randomUUID();
        UUID dept = UUID.randomUUID();
        adapter.save(newFee(patient, dept, null, FeeType.SERVICE, "20000.00"));
        adapter.save(newFee(patient, dept, null, FeeType.SERVICE, "30000.00"));

        assertThat(adapter.findUnpaidByPatient(patient)).hasSize(2);
    }
    @Test
    void findUnpaidByPatient_excludesFeesReservedByAnInvoice() {
        UUID patient = UUID.randomUUID();
        Fee reserved = adapter.save(newFee(patient, UUID.randomUUID(), UUID.randomUUID(), FeeType.EXAM, "10"));
        reserved.assignToInvoice(UUID.randomUUID());
        adapter.save(reserved);
        assertThat(adapter.findUnpaidByPatient(patient)).isEmpty();
    }

}
