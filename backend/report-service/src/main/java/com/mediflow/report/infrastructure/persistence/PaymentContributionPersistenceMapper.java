package com.mediflow.report.infrastructure.persistence;

import com.mediflow.report.domain.model.PaymentContribution;

/** Explicit mapper between the invoice contribution aggregate and its JPA row. */
public final class PaymentContributionPersistenceMapper {

    private PaymentContributionPersistenceMapper() {}

    public static PaymentContribution toDomain(PaymentContributionJpaEntity entity) {
        return PaymentContribution.restore(entity.getInvoiceId(), entity.getCompletedEventId(),
                entity.getFailedEventId(), entity.getPaymentDate(), entity.getDepartmentId(), entity.getAmount(),
                entity.getStatus(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public static PaymentContributionJpaEntity toEntity(PaymentContribution contribution) {
        return PaymentContributionJpaEntity.builder()
                .invoiceId(contribution.getInvoiceId())
                .completedEventId(contribution.getCompletedEventId())
                .failedEventId(contribution.getFailedEventId())
                .paymentDate(contribution.getPaymentDate())
                .departmentId(contribution.getDepartmentId())
                .amount(contribution.getAmount())
                .status(contribution.getStatus())
                .createdAt(contribution.getCreatedAt())
                .updatedAt(contribution.getUpdatedAt())
                .build();
    }
}
