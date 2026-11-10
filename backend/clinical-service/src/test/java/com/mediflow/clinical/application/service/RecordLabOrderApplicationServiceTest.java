package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.clinical.application.dto.authority.LabOrderLifecycleSnapshot;
import com.mediflow.clinical.application.dto.response.RecordLabOrderDTO;
import com.mediflow.clinical.application.exception.UpstreamUnavailableException;
import com.mediflow.clinical.application.port.out.ClinicalLabOrderRepositoryPort;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.LabOrderLifecyclePort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import com.mediflow.clinical.domain.model.ClinicalLabOrder;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.common.exception.ForbiddenOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecordLabOrderApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-10T01:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);

    private final MedicalRecordRepositoryPort records = mock(MedicalRecordRepositoryPort.class);
    private final ClinicalLabOrderRepositoryPort orders = mock(ClinicalLabOrderRepositoryPort.class);
    private final LabOrderLifecyclePort lifecycle = mock(LabOrderLifecyclePort.class);
    private final RecordReadGuard guard = mock(RecordReadGuard.class);
    private final CorrelationIdProvider correlations = mock(CorrelationIdProvider.class);
    private final RecordLabOrderApplicationService service = new RecordLabOrderApplicationService(records, orders, lifecycle, guard, correlations);
    private final UUID patient = UUID.randomUUID();
    private final UUID doctor = UUID.randomUUID();
    private final UUID department = UUID.randomUUID();
    private final UUID appointment = UUID.randomUUID();
    private final MedicalRecord record = MedicalRecord.openForAppointment(patient, doctor, department, DAY, null, appointment, NOW);
    private final ClinicalLabOrder order = ClinicalLabOrder.create(record.getRecordId(), patient, doctor, department, appointment, "CBC", DAY, "CBC_PRICE", NOW);
    private final UUID test = UUID.randomUUID();

    @BeforeEach void setUp() {
        when(correlations.currentOrCreate()).thenReturn(UUID.randomUUID());
        when(records.findById(record.getRecordId())).thenReturn(Optional.of(record));
        when(orders.findByRecordId(record.getRecordId())).thenReturn(List.of(order));
    }

    private LabOrderLifecycleSnapshot live(UUID recordId, String status) {
        return new LabOrderLifecycleSnapshot(order.sourceOrderId(), true, test, recordId, patient, department, "OUTPATIENT_VISIT", appointment, status, NOW);
    }

    @Test void listsLiveStatusForEachOrder() {
        when(lifecycle.lifecycle(eq(order.sourceOrderId()), anyString())).thenReturn(live(record.getRecordId(), "IN_PROGRESS"));
        assertThat(service.list(record.getRecordId())).containsExactly(new RecordLabOrderDTO(order.sourceOrderId(), test, "CBC", "IN_PROGRESS", NOW));
    }

    @Test void orderNotYetInLabIsShownAsNotReceived() {
        when(lifecycle.lifecycle(eq(order.sourceOrderId()), anyString())).thenReturn(
                new LabOrderLifecycleSnapshot(order.sourceOrderId(), false, null, null, null, null, null, null, null, NOW));
        assertThat(service.list(record.getRecordId())).containsExactly(new RecordLabOrderDTO(order.sourceOrderId(), null, "CBC", "NOT_RECEIVED", NOW));
    }

    @Test void labOutageIsUnavailableNotEmpty() {
        when(lifecycle.lifecycle(any(), anyString())).thenThrow(new UpstreamUnavailableException("LAB_LIFECYCLE_UNAVAILABLE", "down"));
        assertThatThrownBy(() -> service.list(record.getRecordId())).isInstanceOfSatisfying(UpstreamUnavailableException.class,
                e -> assertThat(e.getCode()).isEqualTo("LAB_LIFECYCLE_UNAVAILABLE"));
    }

    @Test void malformedLabAnswerIsUnavailable() {
        when(lifecycle.lifecycle(any(), anyString())).thenReturn(null);
        assertThatThrownBy(() -> service.list(record.getRecordId())).isInstanceOfSatisfying(UpstreamUnavailableException.class,
                e -> assertThat(e.getCode()).isEqualTo("LAB_LIFECYCLE_UNAVAILABLE"));
    }

    @Test void orderBoundToAnotherRecordIsRejected() {
        when(lifecycle.lifecycle(eq(order.sourceOrderId()), anyString())).thenReturn(live(UUID.randomUUID(), "IN_PROGRESS"));
        assertThatThrownBy(() -> service.list(record.getRecordId())).isInstanceOfSatisfying(InvalidClinicalDataException.class,
                e -> assertThat(e.getCode()).isEqualTo("CLINICAL_LAB_ORDER_BINDING_MISMATCH"));
    }

    @Test void readFollowsRecordAccessPolicy() {
        doThrow(new ForbiddenOperationException(RecordReadPolicy.DENIED, "denied")).when(guard).requireRecord(record);
        assertThatThrownBy(() -> service.list(record.getRecordId())).isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(lifecycle);
    }
}
