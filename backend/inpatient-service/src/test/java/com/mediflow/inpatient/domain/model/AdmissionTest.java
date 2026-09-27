package com.mediflow.inpatient.domain.model;

import com.mediflow.inpatient.domain.model.enums.AdmissionPriority;
import com.mediflow.inpatient.domain.model.enums.AdmissionStatus;
import com.mediflow.inpatient.domain.model.enums.OverrideType;
import com.mediflow.inpatient.domain.model.enums.SettlementOutcome;
import com.mediflow.inpatient.domain.exception.AdmissionRuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdmissionTest {

    private static final Instant NOW = Instant.parse("2026-09-27T04:00:00Z");

    @Test
    void depositClearanceWithoutBedStaysAwaitingBed() {
        Admission admission = requestedAdmission().markAwaitingBed()
                .applyFinancialClearance(UUID.randomUUID(), null, false, NOW);

        assertEquals(AdmissionStatus.AWAITING_BED, admission.status());
    }

    @Test
    void bedWithoutClearanceWaitsForDepositAndCannotAdmit() {
        Admission admission = requestedAdmission().markAwaitingBed()
                .applyBedAssignment(true, NOW);

        assertEquals(AdmissionStatus.AWAITING_DEPOSIT, admission.status());
        assertThrows(AdmissionRuleViolationException.class,
                () -> admission.admit(NOW, true, null));
    }

    @Test
    void bothBedAndClearanceGuardsMakeAdmissionReady() {
        Admission admission = requestedAdmission().markAwaitingBed()
                .applyFinancialClearance(UUID.randomUUID(), null, false, NOW)
                .applyBedAssignment(true, NOW);

        assertEquals(AdmissionStatus.READY, admission.status());
        assertEquals(AdmissionStatus.ADMITTED,
                admission.admit(NOW.plusSeconds(1), true, null).status());
    }

    @Test
    void emergencyAdmissionPersistsOverrideWithoutCreatingClearance() {
        EmergencyOverride override = new EmergencyOverride(
                UUID.randomUUID(), UUID.randomUUID(), "DOCTOR", "Immediate care", NOW);
        Admission admission = requestedAdmission(true).markAwaitingBed()
                .applyBedAssignment(true, NOW)
                .admit(NOW, true, override);

        assertEquals(AdmissionStatus.ADMITTED, admission.status());
        assertEquals(override.maPheDuyet(), admission.emergencyOverrideId());
        assertNull(admission.depositClearanceId());
    }

    @Test
    void medicalDischargeDoesNotCloseAdmission() {
        Admission admission = readyAdmission().admit(NOW, true, null)
                .medicallyDischarge(UUID.randomUUID(), NOW.plusSeconds(1));

        assertEquals(AdmissionStatus.MEDICALLY_DISCHARGED, admission.status());
        assertNull(admission.closedAt());
    }

    @Test
    void closeRequiresReleasedBedAndAcceptableSettlementOrOverride() {
        Admission discharged = readyAdmission().admit(NOW, true, null)
                .medicallyDischarge(UUID.randomUUID(), NOW.plusSeconds(1));

        assertThrows(AdmissionRuleViolationException.class,
                () -> discharged.close(true, SettlementOutcome.PAID_IN_FULL,
                        UUID.randomUUID(), null, NOW.plusSeconds(2)));
        assertThrows(AdmissionRuleViolationException.class,
                () -> discharged.close(false, SettlementOutcome.ADDITIONAL_PAYMENT_REQUIRED,
                        UUID.randomUUID(), null, NOW.plusSeconds(2)));

        EmergencyOverride override = new EmergencyOverride(
                UUID.randomUUID(), UUID.randomUUID(), "CASHIER", "Approved debt", NOW);
        Admission closed = discharged.close(false, SettlementOutcome.ADDITIONAL_PAYMENT_REQUIRED,
                UUID.randomUUID(), new CloseOverride(override.maPheDuyet(), OverrideType.DEBT_CLOSE,
                        override.nguoiDuyet(), override.vaiTroNguoiDuyet(), override.lyDo(),
                        override.thoiGianDuyet()),
                NOW.plusSeconds(2));

        assertEquals(AdmissionStatus.CLOSED, closed.status());
        assertNotNull(closed.closeOverrideId());
    }

    @Test
    void cancellationIsOnlyAllowedBeforeAdmission() {
        Admission admitted = readyAdmission().admit(NOW, true, null);

        assertThrows(AdmissionRuleViolationException.class,
                () -> admitted.cancel(UUID.randomUUID(), "changed mind", NOW.plusSeconds(1)));
    }

    private static Admission readyAdmission() {
        return requestedAdmission().markAwaitingBed()
                .applyFinancialClearance(UUID.randomUUID(), null, false, NOW)
                .applyBedAssignment(true, NOW);
    }

    private static Admission requestedAdmission() {
        return requestedAdmission(false);
    }

    private static Admission requestedAdmission(boolean emergency) {
        return Admission.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "Pneumonia", NOW, UUID.randomUUID(),
                emergency ? AdmissionPriority.EMERGENCY : AdmissionPriority.ROUTINE, emergency);
    }
}
