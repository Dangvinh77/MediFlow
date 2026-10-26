package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mediflow.clinical.application.dto.authority.LabOrderLifecycleSnapshot;
import com.mediflow.clinical.application.exception.UpstreamUnavailableException;
import com.mediflow.clinical.application.port.out.ClinicalLabOrderRepositoryPort;
import com.mediflow.clinical.application.port.out.LabOrderLifecyclePort;
import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.ClinicalLabOrder;
import com.mediflow.clinical.domain.model.MedicalRecord;

class ClinicalLabCompletionGuardTest {
    private static final Instant NOW = Instant.parse("2026-10-26T03:00:00Z");
    private static final LocalDate DATE = LocalDate.of(2026, 10, 26);
    private static final UUID RECORD_ID = UUID.randomUUID();
    private static final UUID APPOINTMENT_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final UUID DEPARTMENT_ID = UUID.randomUUID();
    private static final UUID TEST_ID = UUID.randomUUID();
    private static final String CORRELATION_ID = UUID.randomUUID().toString();

    private final ClinicalLabOrderRepositoryPort orders = mock(ClinicalLabOrderRepositoryPort.class);
    private final LabOrderLifecyclePort lifecycle = mock(LabOrderLifecyclePort.class);
    private final ClinicalLabOrder order = ClinicalLabOrder.create(RECORD_ID, PATIENT_ID, DOCTOR_ID,
            DEPARTMENT_ID, APPOINTMENT_ID, "CBC", DATE, "CBC_PRICE", NOW);
    private final MedicalRecord record = MedicalRecord.restore(RECORD_ID, PATIENT_ID, DOCTOR_ID,
            DEPARTMENT_ID, DATE, "Symptoms", APPOINTMENT_ID, List.of(), NOW, null);
    private final Appointment appointment = scheduledAppointment();
    private ClinicalLabCompletionGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ClinicalLabCompletionGuard(orders, lifecycle);
        when(orders.findByRecordId(RECORD_ID)).thenReturn(List.of(order));
    }

    @Test
    void unfinishedLabOrder_blocksScheduledRecordCompletion() {
        when(lifecycle.lifecycle(order.sourceOrderId(), CORRELATION_ID))
                .thenReturn(snapshot("IN_PROGRESS", true));

        assertThatThrownBy(() -> guard.requireAllClosed(record, appointment, CORRELATION_ID))
                .isInstanceOf(InvalidClinicalDataException.class)
                .hasFieldOrPropertyWithValue("code", "CLINICAL_LAB_ORDER_PENDING");
    }

    @Test
    void missingLabOrder_blocksScheduledRecordCompletion() {
        when(lifecycle.lifecycle(order.sourceOrderId(), CORRELATION_ID))
                .thenReturn(new LabOrderLifecycleSnapshot(order.sourceOrderId(), false, null, null, null,
                        null, null, null, null, NOW));

        assertThatThrownBy(() -> guard.requireAllClosed(record, appointment, CORRELATION_ID))
                .isInstanceOf(InvalidClinicalDataException.class)
                .hasFieldOrPropertyWithValue("code", "CLINICAL_LAB_ORDER_NOT_FOUND");
    }

    @Test
    void completedAndCancelledLabOrders_allowScheduledRecordCompletion() {
        when(lifecycle.lifecycle(order.sourceOrderId(), CORRELATION_ID))
                .thenReturn(snapshot("COMPLETED", true), snapshot("CANCELLED", true));

        assertThatCode(() -> guard.requireAllClosed(record, appointment, CORRELATION_ID)).doesNotThrowAnyException();
        assertThatCode(() -> guard.requireAllClosed(record, appointment, CORRELATION_ID)).doesNotThrowAnyException();
    }

    @Test
    void mismatchedLabEpisode_bindingBlocksScheduledRecordCompletion() {
        LabOrderLifecycleSnapshot mismatch = new LabOrderLifecycleSnapshot(order.sourceOrderId(), true, TEST_ID,
                RECORD_ID, PATIENT_ID, DEPARTMENT_ID, "OUTPATIENT_VISIT", UUID.randomUUID(), "COMPLETED", NOW);
        when(lifecycle.lifecycle(order.sourceOrderId(), CORRELATION_ID)).thenReturn(mismatch);

        assertThatThrownBy(() -> guard.requireAllClosed(record, appointment, CORRELATION_ID))
                .isInstanceOf(InvalidClinicalDataException.class)
                .hasFieldOrPropertyWithValue("code", "CLINICAL_LAB_ORDER_BINDING_MISMATCH");
    }

    @Test
    void unavailableLabLifecycle_failsClosed() {
        when(lifecycle.lifecycle(order.sourceOrderId(), CORRELATION_ID))
                .thenThrow(new UpstreamUnavailableException("LAB_LIFECYCLE_UNAVAILABLE", "Lab unavailable"));

        assertThatThrownBy(() -> guard.requireAllClosed(record, appointment, CORRELATION_ID))
                .isInstanceOf(UpstreamUnavailableException.class)
                .hasFieldOrPropertyWithValue("code", "LAB_LIFECYCLE_UNAVAILABLE");
    }

    private LabOrderLifecycleSnapshot snapshot(String status, boolean exists) {
        return new LabOrderLifecycleSnapshot(order.sourceOrderId(), exists, exists ? TEST_ID : null,
                exists ? RECORD_ID : null, exists ? PATIENT_ID : null, exists ? DEPARTMENT_ID : null,
                exists ? "OUTPATIENT_VISIT" : null, exists ? APPOINTMENT_ID : null, exists ? status : null, NOW);
    }

    private static Appointment scheduledAppointment() {
        Appointment appointment = Appointment.createScheduled(APPOINTMENT_ID, PATIENT_ID, DOCTOR_ID,
                DEPARTMENT_ID, DATE, LocalTime.NOON, "Visit", NOW, NOW);
        appointment.confirmScheduledPayment(UUID.randomUUID(), NOW);
        appointment.startScheduledExam(appointment.getExamClearanceId(), NOW);
        return appointment;
    }
}
