package com.mediflow.pharmacy.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mediflow.pharmacy.application.port.out.LifecycleReconciliationRepositoryPort;

/** PostgreSQL proof that reconciliation detects mismatches without repairing rows. */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "mediflow.pharmacy.outbox.enabled=false",
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes",
        "mediflow.pharmacy.reservation.release-cron=-",
        "mediflow.pharmacy.reservation.reconciliation-cron=-"
})
@Testcontainers(disabledWithoutDocker = true)
class LifecycleReconciliationPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LifecycleReconciliationRepositoryPort reconciliation;

    private UUID prescriptionId;
    private UUID drugId;

    /** Deletes only rows created by this test in foreign-key order. */
    @AfterEach
    void clean() {
        if (prescriptionId != null) {
            jdbc.update("DELETE FROM STOCK_RESERVATION WHERE prescription_id = ?", prescriptionId);
            jdbc.update("DELETE FROM DISPENSE_SLIP WHERE prescription_id = ?", prescriptionId);
            jdbc.update("DELETE FROM PRESCRIPTION_LINE WHERE prescription_id = ?", prescriptionId);
            jdbc.update("DELETE FROM PRESCRIPTION WHERE prescription_id = ?", prescriptionId);
            if (drugId != null) {
                jdbc.update("DELETE FROM DRUG WHERE drug_id = ?", drugId);
            }
        }
    }

    /** Reports an active prescription whose slip is terminal and leaves all rows untouched. */
    @Test
    void activeTerminalSlip_isReportedAsDryRunFinding() {
        drugId = UUID.randomUUID();
        prescriptionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO DRUG (drug_id, drug_name, active_ingredient, unit, price, stock_quantity,
                    expiry_date, low_stock_threshold, created_at)
                VALUES (?, 'Recon drug', 'ingredient', 'tablet', ?, 10, ?, 1, now())
                """, drugId, new BigDecimal("1.00"), LocalDate.now().plusDays(2));
        jdbc.update("""
                INSERT INTO PRESCRIPTION (prescription_id, record_id, patient_id, doctor_id, department_id,
                    prescribed_date, total_amount, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', now())
                """, prescriptionId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), LocalDate.now(), new BigDecimal("1.00"));
        jdbc.update("""
                INSERT INTO PRESCRIPTION_LINE (line_id, prescription_id, drug_id, quantity, unit_price, line_total)
                VALUES (?, ?, ?, 1, ?, ?)
                """, UUID.randomUUID(), prescriptionId, drugId,
                new BigDecimal("1.00"), new BigDecimal("1.00"));
        jdbc.update("""
                INSERT INTO DISPENSE_SLIP (dispense_id, prescription_id, status, created_at)
                VALUES (?, ?, 'DISPENSED', now())
                """, UUID.randomUUID(), prescriptionId);
        jdbc.update("""
                INSERT INTO STOCK_RESERVATION (reservation_id, drug_id, prescription_id, quantity,
                    status, expires_at, created_at)
                VALUES (?, ?, ?, 2, 'FULFILLED', now() + interval '1 day', now())
                """, UUID.randomUUID(), drugId, prescriptionId);

        assertThat(reconciliation.findMismatches(10))
                .anySatisfy(finding -> {
                    assertThat(finding.prescriptionId()).isEqualTo(prescriptionId);
                    assertThat(finding.anomalyType()).isEqualTo("ACTIVE_SLIP_MISMATCH");
                });
        // Prescription stays ACTIVE (not terminal) with a FULFILLED reservation, so the matching
        // anomaly is ACTIVE_RESERVATION_TERMINAL — TERMINAL_RESERVATION_STATUS_MISMATCH only fires
        // for prescriptions whose own status is already terminal (FULFILLED/CANCELLED/EXPIRED/
        // DISPENSE_FAILED), which this fixture never reaches.
        assertThat(reconciliation.findMismatches(20))
                .extracting(finding -> finding.anomalyType())
                .contains("RESERVATION_QUANTITY_MISMATCH", "ACTIVE_RESERVATION_TERMINAL");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM PRESCRIPTION WHERE prescription_id = ?", String.class, prescriptionId))
                .isEqualTo("ACTIVE");
    }
}
