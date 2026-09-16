package com.mediflow.report.infrastructure.persistence.adapter;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.report.application.port.out.PaymentContributionRepositoryPort;
import com.mediflow.report.domain.model.PaymentContribution;
import com.mediflow.report.domain.model.PaymentContributionStatus;
import com.mediflow.report.infrastructure.persistence.PaymentContributionJpaEntity;
import com.mediflow.report.infrastructure.persistence.PaymentContributionPersistenceMapper;
import com.mediflow.report.infrastructure.persistence.repository.PaymentContributionJpaRepository;

import lombok.RequiredArgsConstructor;

/** Invoice contribution adapter using an advisory transaction lock for absent rows. */
@Component
@RequiredArgsConstructor
public class PaymentContributionPersistenceAdapter implements PaymentContributionRepositoryPort {

    private final PaymentContributionJpaRepository repository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public PaymentContribution findOrCreateForUpdate(UUID invoiceId) {
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(hashtextextended(CAST(? AS text), 0))",
                statement -> statement.setObject(1, invoiceId), resultSet -> {
                    while (resultSet.next()) {
                        // Consume the single void result; the lock is held until transaction end.
                    }
                    return null;
                });
        return repository.findById(invoiceId)
                .map(PaymentContributionPersistenceMapper::toDomain)
                .orElseGet(() -> PaymentContribution.initialize(invoiceId));
    }

    @Override
    @Transactional
    public PaymentContribution save(PaymentContribution contribution) {
        if (contribution.getStatus() == PaymentContributionStatus.NEW) {
            throw new IllegalArgumentException("NEW payment contribution must not be persisted");
        }
        PaymentContributionJpaEntity saved = repository.save(PaymentContributionPersistenceMapper.toEntity(contribution));
        return PaymentContributionPersistenceMapper.toDomain(saved);
    }
}
