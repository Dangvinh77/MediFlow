package com.mediflow.billing.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

import com.mediflow.billing.application.port.out.InvoiceRepositoryPort.DepartmentRevenue;
import com.mediflow.billing.domain.model.Fee;
import com.mediflow.billing.domain.model.FeeType;
import com.mediflow.billing.domain.model.Invoice;
import com.mediflow.billing.domain.model.PaymentMethod;
import com.mediflow.billing.domain.model.SagaStatus;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;

/**
 * Slice test cho {@link InvoicePersistenceAdapter}: ánh xạ entity ↔ domain, phân trang, unique
 * partial index {@code uq_invoice_prescription} (BR-B6), và truy vấn tổng hợp doanh thu theo khoa
 * (BR-B10). {@link FeePersistenceAdapter} được import để dựng dữ liệu phí gắn hóa đơn.
 */
@DataJpaTest
@Import({InvoicePersistenceAdapter.class, FeePersistenceAdapter.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class InvoicePersistenceAdapterTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private InvoicePersistenceAdapter adapter;

    @Autowired
    private FeePersistenceAdapter feeAdapter;

    private static Invoice paidInvoice(UUID patientId, UUID prescriptionId, String total, Instant paidAt) {
        return Invoice.restore(null, patientId, LocalDate.of(2026, 9, 8), new BigDecimal(total), true,
                PaymentMethod.CASH, null, prescriptionId, SagaStatus.NONE, paidAt, null, null);
    }

    private static Instant day(int dayOfMonth) {
        return LocalDate.of(2026, 9, dayOfMonth).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private void attachPaidFee(UUID invoiceId, UUID patientId, UUID departmentId, String amount) {
        Fee fee = Fee.create(patientId, UUID.randomUUID(), departmentId, UUID.randomUUID(),
                FeeType.SERVICE, LocalDate.of(2026, 9, 8), new BigDecimal(amount));
        fee.assignToInvoice(invoiceId);
        fee.markPaid();
        feeAdapter.save(fee);
    }

    @Test
    void save_thenFindById_roundTripsSagaFields() {
        Invoice saved = adapter.save(Invoice.restore(null, UUID.randomUUID(), LocalDate.of(2026, 9, 8),
                new BigDecimal("300000.00"), false, null, null, UUID.randomUUID(),
                SagaStatus.AWAITING_PAYMENT, null, null, null));

        Invoice reloaded = adapter.findById(saved.getInvoiceId()).orElseThrow();
        assertThat(reloaded.getSagaStatus()).isEqualTo(SagaStatus.AWAITING_PAYMENT);
        assertThat(reloaded.getTotalAmount()).isEqualByComparingTo("300000.00");
        assertThat(reloaded.isPaid()).isFalse();
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    void findByPrescription_returnsTheSagaInvoice() {
        UUID prescriptionId = UUID.randomUUID();
        adapter.save(paidInvoice(UUID.randomUUID(), prescriptionId, "500000.00", day(10)));

        assertThat(adapter.findByPrescription(prescriptionId)).isPresent();
        assertThat(adapter.findByPrescription(UUID.randomUUID())).isEmpty();
    }

    @Test
    void save_secondInvoiceForSamePrescription_violatesUniquePartialIndex() {
        UUID prescriptionId = UUID.randomUUID();
        adapter.save(paidInvoice(UUID.randomUUID(), prescriptionId, "500000.00", day(10)));

        assertThatThrownBy(() -> adapter.save(paidInvoice(UUID.randomUUID(), prescriptionId, "1.00", day(11))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_multipleInvoicesWithNullPrescription_isAllowed() {
        UUID patient = UUID.randomUUID();
        adapter.save(paidInvoice(patient, null, "10000.00", day(10)));
        adapter.save(paidInvoice(patient, null, "20000.00", day(11)));

        assertThat(adapter.findByPatient(patient, new PageQuery(0, 20)).totalElements()).isEqualTo(2);
    }

    @Test
    void findByPatient_paginatesAndKeepsMetadata() {
        UUID patient = UUID.randomUUID();
        for (int i = 1; i <= 3; i++) {
            adapter.save(paidInvoice(patient, null, i + "0000.00", day(10 + i)));
        }

        PageResult<Invoice> firstPage = adapter.findByPatient(patient, new PageQuery(0, 2));

        assertThat(firstPage.content()).hasSize(2);
        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(firstPage.number()).isZero();
        assertThat(firstPage.size()).isEqualTo(2);
    }

    @Test
    void sumRevenueByDepartment_groupsByFeeDepartment_countsDistinctInvoices_withinDateRange() {
        UUID deptA = UUID.randomUUID();
        UUID deptB = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);

        // Hóa đơn 1 (đã trả, trong khoảng): 2 phí khoa A → count khoa A = 1, tổng A = 300k
        UUID inv1 = adapter.save(paidInvoice(patient, null, "300000.00", day(15))).getInvoiceId();
        attachPaidFee(inv1, patient, deptA, "100000.00");
        attachPaidFee(inv1, patient, deptA, "200000.00");

        // Hóa đơn 2 (đã trả, trong khoảng): 1 phí khoa A + 1 phí khoa B
        UUID inv2 = adapter.save(paidInvoice(patient, null, "150000.00", day(20))).getInvoiceId();
        attachPaidFee(inv2, patient, deptA, "50000.00");
        attachPaidFee(inv2, patient, deptB, "100000.00");

        // Hóa đơn 3: đã trả nhưng NGOÀI khoảng ngày → không tính
        UUID inv3 = adapter.save(paidInvoice(patient, null, "999999.00", day(30).plusSeconds(86400))).getInvoiceId();
        attachPaidFee(inv3, patient, deptA, "999999.00");

        List<DepartmentRevenue> rows = adapter.sumRevenueByDepartment(null, from, to);

        assertThat(rows).hasSize(2);
        DepartmentRevenue a = rows.stream().filter(r -> r.departmentId().equals(deptA)).findFirst().orElseThrow();
        DepartmentRevenue b = rows.stream().filter(r -> r.departmentId().equals(deptB)).findFirst().orElseThrow();
        assertThat(a.totalRevenue()).isEqualByComparingTo("350000.00");   // 100k + 200k + 50k
        assertThat(a.invoiceCount()).isEqualTo(2);                        // inv1, inv2
        assertThat(b.totalRevenue()).isEqualByComparingTo("100000.00");
        assertThat(b.invoiceCount()).isEqualTo(1);
    }

    @Test
    void sumRevenueByDepartment_filtersByDepartmentWhenGiven_andExcludesUnpaidInvoices() {
        UUID deptA = UUID.randomUUID();
        UUID deptB = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);

        UUID paid = adapter.save(paidInvoice(patient, null, "100000.00", day(10))).getInvoiceId();
        attachPaidFee(paid, patient, deptA, "100000.00");
        attachPaidFee(paid, patient, deptB, "100000.00");

        // Hóa đơn chưa thanh toán → không vào doanh thu dù có phí khoa A
        UUID unpaidInvoice = adapter.save(Invoice.restore(null, patient, LocalDate.of(2026, 9, 8),
                new BigDecimal("100000.00"), false, null, null, null, SagaStatus.NONE, null, null, null))
                .getInvoiceId();
        Fee unpaidFee = Fee.create(patient, UUID.randomUUID(), deptA, UUID.randomUUID(),
                FeeType.SERVICE, LocalDate.of(2026, 9, 8), new BigDecimal("777.00"));
        unpaidFee.assignToInvoice(unpaidInvoice);
        feeAdapter.save(unpaidFee);

        List<DepartmentRevenue> rows = adapter.sumRevenueByDepartment(deptA, from, to);

        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.departmentId()).isEqualTo(deptA);
            assertThat(r.totalRevenue()).isEqualByComparingTo("100000.00");
            assertThat(r.invoiceCount()).isEqualTo(1);
        });
    }
    @Test
    void mutationLookupsReturnTheSameInvoiceUnderLock() {
        UUID prescription = UUID.randomUUID();
        Invoice saved = adapter.save(paidInvoice(UUID.randomUUID(), prescription, "10", day(10)));
        assertThat(adapter.findByIdForUpdate(saved.getInvoiceId()).orElseThrow().getInvoiceId())
                .isEqualTo(saved.getInvoiceId());
        assertThat(adapter.findByPrescriptionForUpdate(prescription).orElseThrow().getInvoiceId())
                .isEqualTo(saved.getInvoiceId());
    }

}
