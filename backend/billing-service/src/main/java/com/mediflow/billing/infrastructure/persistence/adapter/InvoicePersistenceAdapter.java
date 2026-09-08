package com.mediflow.billing.infrastructure.persistence.adapter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.mediflow.billing.application.port.out.InvoiceRepositoryPort;
import com.mediflow.billing.domain.model.Invoice;
import com.mediflow.billing.infrastructure.persistence.jpaEntity.InvoiceJpaEntity;
import com.mediflow.billing.infrastructure.persistence.repository.InvoiceJpaRepository;
import com.mediflow.billing.infrastructure.persistence.repository.RevenueRow;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;

import lombok.RequiredArgsConstructor;

/**
 * Adapter cho {@link InvoiceRepositoryPort}. Đổi {@link PageQuery}/{@link PageResult} của common
 * sang/từ Spring Data {@code Pageable}/{@code Page} tại đây — tầng application không bao giờ thấy
 * kiểu Spring Data. Map entity ↔ domain thủ công.
 */
@Component
@RequiredArgsConstructor
public class InvoicePersistenceAdapter implements InvoiceRepositoryPort {

    private final InvoiceJpaRepository jpaRepo;

    @Override
    public Invoice save(Invoice invoice) {
        return toDomain(jpaRepo.saveAndFlush(toEntity(invoice)));
    }

    @Override
    public Optional<Invoice> findById(UUID id) {
        return jpaRepo.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Invoice> findByPrescription(UUID prescriptionId) {
        return jpaRepo.findByPrescriptionId(prescriptionId).map(this::toDomain);
    }

    @Override
    public PageResult<Invoice> findByPatient(UUID patientId, PageQuery page) {
        Page<InvoiceJpaEntity> found = jpaRepo.findByPatientId(patientId,
                PageRequest.of(page.page(), page.size(), Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResult.of(
                found.getContent().stream().map(this::toDomain).toList(),
                found.getTotalElements(), page.page(), page.size());
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code fromDate}/{@code toDate} (DATE, bao gồm cả hai đầu) được đổi sang khoảng nửa mở
     * {@code [fromDate 00:00Z, toDate+1 00:00Z)} để so với {@code paid_at} (TIMESTAMPTZ). Mốc quy
     * chiếu UTC — trùng với {@code now()} mặc định của cột.
     */
    @Override
    public List<DepartmentRevenue> sumRevenueByDepartment(UUID departmentId, LocalDate fromDate, LocalDate toDate) {
        Instant fromTs = fromDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toTs = toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return jpaRepo.sumRevenueByDepartment(departmentId, fromTs, toTs).stream()
                .map(InvoicePersistenceAdapter::toProjection)
                .toList();
    }

    // ---- map ----

    private static DepartmentRevenue toProjection(RevenueRow row) {
        return new DepartmentRevenue(row.departmentId(), row.totalRevenue(),
                row.invoiceCount() == null ? 0L : row.invoiceCount());
    }

    private Invoice toDomain(InvoiceJpaEntity e) {
        return Invoice.restore(
                e.getInvoiceId(), e.getPatientId(), e.getCreatedDate(), e.getTotalAmount(), e.isPaid(),
                e.getPaymentMethod(), e.getDispenseId(), e.getPrescriptionId(), e.getSagaStatus(),
                e.getPaidAt(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private InvoiceJpaEntity toEntity(Invoice i) {
        return InvoiceJpaEntity.builder()
                .invoiceId(i.getInvoiceId())
                .patientId(i.getPatientId())
                .createdDate(i.getCreatedDate())
                .totalAmount(i.getTotalAmount())
                .isPaid(i.isPaid())
                .paymentMethod(i.getPaymentMethod())
                .dispenseId(i.getDispenseId())
                .prescriptionId(i.getPrescriptionId())
                .sagaStatus(i.getSagaStatus())
                .paidAt(i.getPaidAt())
                .createdAt(i.getCreatedAt())
                .updatedAt(i.getUpdatedAt())
                .build();
    }
}
