package com.mediflow.pharmacy.infrastructure.persistence.adapter;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.pharmacy.application.port.out.ReservationExpiryLeaseClaim;
import com.mediflow.pharmacy.application.port.out.ReservationExpiryLeaseRepositoryPort;
import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.PharmacySchedulerLeaseJpaEntity;
import com.mediflow.pharmacy.infrastructure.persistence.repository.PharmacySchedulerLeaseJpaRepository;

import lombok.RequiredArgsConstructor;

/** PostgreSQL adapter implementing atomic scheduler cursor/lease operations. */
@Component
@RequiredArgsConstructor
public class PharmacySchedulerLeasePersistenceAdapter implements ReservationExpiryLeaseRepositoryPort {

    private final PharmacySchedulerLeaseJpaRepository repository;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public Optional<ReservationExpiryLeaseClaim> tryAcquire(
            String jobName, String owner, Instant now, Duration leaseDuration) {
        requireArguments(jobName, owner, now, leaseDuration);
        Instant leaseUntil = now.plus(leaseDuration);
        UUID leaseToken = UUID.randomUUID();
        if (repository.tryAcquire(jobName, owner, leaseToken, leaseUntil, now) == 0) {
            return Optional.empty();
        }
        return repository.findById(jobName)
                .map(entity -> new ReservationExpiryLeaseClaim(entity.getCursorId(), entity.getLeaseToken()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public boolean advance(String jobName, String owner, UUID leaseToken, UUID cursor, Instant now) {
        if (leaseToken == null || cursor == null || now == null) {
            throw new IllegalArgumentException("lease token, cursor and now are required");
        }
        return repository.advance(jobName, owner, leaseToken, cursor, now) == 1;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void release(String jobName, String owner, UUID leaseToken, Instant now) {
        if (leaseToken == null || now == null) {
            throw new IllegalArgumentException("lease token and now are required");
        }
        repository.release(jobName, owner, leaseToken, now);
    }

    private void requireArguments(String jobName, String owner, Instant now, Duration leaseDuration) {
        if (jobName == null || jobName.isBlank() || owner == null || owner.isBlank() || now == null
                || leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("Invalid scheduler lease arguments");
        }
    }
}
