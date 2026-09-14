package com.mediflow.pharmacy.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.PharmacySchedulerLeaseJpaEntity;

/** Atomic PostgreSQL operations for scheduler cursor/lease ownership. */
public interface PharmacySchedulerLeaseJpaRepository
        extends JpaRepository<PharmacySchedulerLeaseJpaEntity, String> {

    /** Claims a row only when the previous lease is absent or expired. */
    @Modifying
    @Query(value = """
            INSERT INTO PHARMACY_SCHEDULER_LEASE
                (job_name, cursor_id, lease_owner, lease_token, lease_until, updated_at)
            VALUES (:jobName, NULL, :owner, :leaseToken, :leaseUntil, :now)
            ON CONFLICT (job_name) DO UPDATE
                SET lease_owner = :owner,
                    lease_token = :leaseToken,
                    lease_until = :leaseUntil,
                    updated_at = :now
                WHERE PHARMACY_SCHEDULER_LEASE.lease_until IS NULL
                   OR PHARMACY_SCHEDULER_LEASE.lease_until <= :now
            """, nativeQuery = true)
    int tryAcquire(
            @Param("jobName") String jobName,
            @Param("owner") String owner,
            @Param("leaseToken") UUID leaseToken,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now);

    /** Moves the cursor only while the owner and fencing token are still live. */
    @Modifying
    @Query(value = """
            UPDATE PHARMACY_SCHEDULER_LEASE
               SET cursor_id = :cursorId, updated_at = :now
             WHERE job_name = :jobName
               AND lease_owner = :owner
               AND lease_token = :leaseToken
               AND lease_until > :now
            """, nativeQuery = true)
    int advance(
            @Param("jobName") String jobName,
            @Param("owner") String owner,
            @Param("leaseToken") UUID leaseToken,
            @Param("cursorId") java.util.UUID cursorId,
            @Param("now") Instant now);

    /** Releases a lease without allowing an old fencing token to clear a newer lease. */
    @Modifying
    @Query(value = """
            UPDATE PHARMACY_SCHEDULER_LEASE
               SET lease_owner = NULL, lease_until = NULL, updated_at = :now
             WHERE job_name = :jobName
               AND lease_owner = :owner
               AND lease_token = :leaseToken
            """, nativeQuery = true)
    int release(
            @Param("jobName") String jobName,
            @Param("owner") String owner,
            @Param("leaseToken") UUID leaseToken,
            @Param("now") Instant now);
}
