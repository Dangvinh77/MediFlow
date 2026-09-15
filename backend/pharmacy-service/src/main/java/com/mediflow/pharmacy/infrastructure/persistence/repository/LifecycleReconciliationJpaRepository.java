package com.mediflow.pharmacy.infrastructure.persistence.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.mediflow.pharmacy.infrastructure.persistence.jpaentity.PrescriptionJpaEntity;

/** Read-only native queries for lifecycle/reservation reconciliation. */
public interface LifecycleReconciliationJpaRepository
        extends JpaRepository<PrescriptionJpaEntity, java.util.UUID> {

    /**
     * Finds known and unknown lifecycle inconsistencies without locking or mutating rows.
     * The UNION branches keep each anomaly explicit so operators can repair only reviewed data.
     */
    @Query(value = """
            SELECT p.prescription_id AS prescription_id,
                   'MISSING_DISPENSE_SLIP' AS anomaly_type,
                   'prescription has no dispense slip' AS details
              FROM PRESCRIPTION p
             WHERE NOT EXISTS (SELECT 1 FROM DISPENSE_SLIP d
                                WHERE d.prescription_id = p.prescription_id)
            UNION ALL
            SELECT p.prescription_id, 'ACTIVE_SLIP_MISMATCH',
                   'active prescription requires PENDING slip'
              FROM PRESCRIPTION p JOIN DISPENSE_SLIP d
                ON d.prescription_id = p.prescription_id
             WHERE p.status = 'ACTIVE' AND d.status <> 'PENDING'
            UNION ALL
            SELECT p.prescription_id, 'ACTIVE_RESERVATION_SET_MISMATCH',
                   'active prescription lines and reservations differ'
              FROM PRESCRIPTION p
             WHERE p.status = 'ACTIVE'
               AND (EXISTS (SELECT 1 FROM PRESCRIPTION_LINE l
                             WHERE l.prescription_id = p.prescription_id
                               AND NOT EXISTS (SELECT 1 FROM STOCK_RESERVATION r
                                                WHERE r.prescription_id = p.prescription_id
                                                  AND r.drug_id = l.drug_id))
                    OR EXISTS (SELECT 1 FROM STOCK_RESERVATION r
                                WHERE r.prescription_id = p.prescription_id
                                  AND NOT EXISTS (SELECT 1 FROM PRESCRIPTION_LINE l
                                                   WHERE l.prescription_id = p.prescription_id
                                                   AND l.drug_id = r.drug_id)))
            UNION ALL
            SELECT p.prescription_id, 'TERMINAL_RESERVATION_SET_MISMATCH',
                   'terminal prescription lines and reservations differ'
              FROM PRESCRIPTION p
             WHERE p.status IN ('FULFILLED', 'CANCELLED', 'EXPIRED', 'DISPENSE_FAILED')
               AND (EXISTS (SELECT 1 FROM PRESCRIPTION_LINE l
                             WHERE l.prescription_id = p.prescription_id
                               AND NOT EXISTS (SELECT 1 FROM STOCK_RESERVATION r
                                                WHERE r.prescription_id = p.prescription_id
                                                  AND r.drug_id = l.drug_id))
                    OR EXISTS (SELECT 1 FROM STOCK_RESERVATION r
                                WHERE r.prescription_id = p.prescription_id
                                  AND NOT EXISTS (SELECT 1 FROM PRESCRIPTION_LINE l
                                                   WHERE l.prescription_id = p.prescription_id
                                                     AND l.drug_id = r.drug_id)))
            UNION ALL
            SELECT p.prescription_id, 'ACTIVE_RESERVATION_TERMINAL',
                   'active prescription contains a terminal reservation'
              FROM PRESCRIPTION p JOIN STOCK_RESERVATION r
                ON r.prescription_id = p.prescription_id
             WHERE p.status = 'ACTIVE' AND r.status <> 'RESERVED'
            UNION ALL
            SELECT p.prescription_id, 'RESERVATION_QUANTITY_MISMATCH',
                   'reservation quantity differs from prescription line quantity'
              FROM PRESCRIPTION p
              JOIN PRESCRIPTION_LINE l ON l.prescription_id = p.prescription_id
              JOIN STOCK_RESERVATION r
                ON r.prescription_id = p.prescription_id AND r.drug_id = l.drug_id
             WHERE r.quantity <> l.quantity
            UNION ALL
            SELECT p.prescription_id, 'TERMINAL_RESERVATION_STATUS_MISMATCH',
                   'reservation status does not match terminal prescription status'
              FROM PRESCRIPTION p JOIN STOCK_RESERVATION r
                ON r.prescription_id = p.prescription_id
             WHERE p.status IN ('FULFILLED', 'CANCELLED', 'EXPIRED', 'DISPENSE_FAILED')
               AND r.status <> CASE p.status
                   WHEN 'FULFILLED' THEN 'FULFILLED'
                   WHEN 'EXPIRED' THEN 'EXPIRED'
                   ELSE 'RELEASED'
               END
            UNION ALL
            SELECT p.prescription_id, 'TERMINAL_RESERVATION_STILL_RESERVED',
                   'terminal prescription retains RESERVED stock'
              FROM PRESCRIPTION p JOIN STOCK_RESERVATION r
                ON r.prescription_id = p.prescription_id
             WHERE p.status IN ('FULFILLED', 'CANCELLED', 'EXPIRED', 'DISPENSE_FAILED')
               AND r.status = 'RESERVED'
            UNION ALL
            SELECT p.prescription_id, 'FULFILLED_SLIP_MISMATCH',
                   'fulfilled prescription requires DISPENSED slip'
              FROM PRESCRIPTION p JOIN DISPENSE_SLIP d
                ON d.prescription_id = p.prescription_id
             WHERE p.status = 'FULFILLED' AND d.status <> 'DISPENSED'
            UNION ALL
            SELECT p.prescription_id, 'CANCELLED_SLIP_MISMATCH',
                   'cancelled prescription requires CANCELLED slip'
              FROM PRESCRIPTION p JOIN DISPENSE_SLIP d
                ON d.prescription_id = p.prescription_id
             WHERE p.status = 'CANCELLED' AND d.status <> 'CANCELLED'
            UNION ALL
            SELECT p.prescription_id, 'EXPIRED_SLIP_MISMATCH',
                   'expired prescription requires EXPIRED slip'
              FROM PRESCRIPTION p JOIN DISPENSE_SLIP d
                ON d.prescription_id = p.prescription_id
             WHERE p.status = 'EXPIRED' AND d.status <> 'EXPIRED'
            UNION ALL
            SELECT p.prescription_id, 'DISPENSE_FAILED_SLIP_MISMATCH',
                   'dispense-failed prescription requires FAILED slip'
              FROM PRESCRIPTION p JOIN DISPENSE_SLIP d
                ON d.prescription_id = p.prescription_id
             WHERE p.status = 'DISPENSE_FAILED' AND d.status <> 'FAILED'
            UNION ALL
            SELECT p.prescription_id, 'UNKNOWN_PRESCRIPTION_STATUS',
                   'prescription status is outside the lifecycle contract'
              FROM PRESCRIPTION p
             WHERE p.status NOT IN ('ACTIVE', 'FULFILLED', 'CANCELLED', 'EXPIRED', 'DISPENSE_FAILED')
            UNION ALL
            SELECT p.prescription_id, 'UNKNOWN_SLIP_STATUS',
                   'dispense slip status is outside the lifecycle contract'
              FROM PRESCRIPTION p JOIN DISPENSE_SLIP d
                ON d.prescription_id = p.prescription_id
             WHERE d.status NOT IN ('PENDING', 'DISPENSED', 'FAILED', 'CANCELLED', 'EXPIRED')
            UNION ALL
            SELECT p.prescription_id, 'UNKNOWN_RESERVATION_STATUS',
                   'reservation status is outside the lifecycle contract'
              FROM PRESCRIPTION p JOIN STOCK_RESERVATION r
                ON r.prescription_id = p.prescription_id
             WHERE r.status NOT IN ('RESERVED', 'FULFILLED', 'RELEASED', 'EXPIRED')
            ORDER BY prescription_id, anomaly_type
            """, nativeQuery = true)
    List<LifecycleMismatchProjection> findMismatches(Pageable pageable);
}
