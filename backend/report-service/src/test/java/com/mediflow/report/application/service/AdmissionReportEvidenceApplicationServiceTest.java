package com.mediflow.report.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.mediflow.report.application.mapper.AdmissionReportFactMapper;
import com.mediflow.report.application.port.out.AdmissionReportEvidencePort;
import com.mediflow.report.domain.model.AdmissionReportHistory;
import com.mediflow.report.domain.model.AdmissionReportHistory.Status;
import com.mediflow.report.support.AdmissionEvidenceTestFixtures;

class AdmissionReportEvidenceApplicationServiceTest {
    private final AdmissionReportEvidencePort evidence = mock(AdmissionReportEvidencePort.class);
    private final AdmissionReportFactMapper mapper = new AdmissionReportFactMapper();
    private final AdmissionReportEvidenceApplicationService service = new AdmissionReportEvidenceApplicationService(evidence, mapper);

    @Test void actualCloseWithoutDepartment_storesPendingNotSyntheticMetric() throws Exception {
        var event = AdmissionEvidenceTestFixtures.fixture("admission.closed");
        when(evidence.claimDelivery(event)).thenReturn(true);
        when(evidence.findHistory(event.metadata().sourceId())).thenReturn(new AdmissionReportHistory(event.metadata().sourceId(), null, null));
        var history = service.project(event);
        assertThat(history.status()).isEqualTo(Status.PENDING_START);
        assertThat(history.departmentId()).isNull();
        verify(evidence).store(mapper.map(event));
        var order = inOrder(evidence);
        order.verify(evidence).lockAdmission(event.metadata().sourceId());
        order.verify(evidence).claimDelivery(event);
        order.verify(evidence).findHistory(event.metadata().sourceId());
        order.verify(evidence).store(any());
    }
    @Test void actualLateStart_pairsExactPatientAndDepartmentWithoutRevisionFallback() throws Exception {
        var close = AdmissionEvidenceTestFixtures.fixture("admission.closed");
        var start = AdmissionEvidenceTestFixtures.fixture("admission.started");
        var pending = new AdmissionReportHistory(close.metadata().sourceId(), null, mapper.map(close));
        when(evidence.findHistory(start.metadata().sourceId())).thenReturn(pending);
        when(evidence.claimDelivery(start)).thenReturn(true);
        var paired = service.project(start);
        assertThat(paired.status()).isEqualTo(Status.CLOSED);
        assertThat(paired.departmentId().toString()).isEqualTo(start.payload().get("departmentId"));
        assertThat(start.payload()).doesNotContainKey("sourceRevision");
        verify(evidence).store(mapper.map(start));
    }
    @Test void semanticDuplicateWithNewEventId_retainsDeliveryWithoutRewritingFact() throws Exception {
        var event = AdmissionEvidenceTestFixtures.fixture("admission.started");
        when(evidence.findHistory(event.metadata().sourceId())).thenReturn(new AdmissionReportHistory(event.metadata().sourceId(), mapper.map(event), null));
        when(evidence.claimDelivery(event)).thenReturn(true);
        service.project(event);
        verify(evidence, never()).store(any());
    }
    @Test void changedPatient_conflictBeforeStore() throws Exception {
        var start = AdmissionEvidenceTestFixtures.fixture("admission.started");
        var close = AdmissionEvidenceTestFixtures.changed(AdmissionEvidenceTestFixtures.fixture("admission.closed"), UUID.randomUUID(), "patientId", UUID.randomUUID().toString());
        when(evidence.findHistory(close.metadata().sourceId())).thenReturn(new AdmissionReportHistory(close.metadata().sourceId(), mapper.map(start), null));
        when(evidence.claimDelivery(close)).thenReturn(true);
        assertThatThrownBy(() -> service.project(close)).hasMessageContaining("patient");
        verify(evidence, never()).store(any());
    }
    @ParameterizedTest @ValueSource(strings = {"patientId", "departmentId", "bedId", "admittedAt", "emergency"})
    void missingStartField_rejectsBeforeClaim(String field) throws Exception {
        var event = AdmissionEvidenceTestFixtures.fixture("admission.started");
        var invalid = AdmissionEvidenceTestFixtures.changed(event, UUID.randomUUID(), field, null);
        assertThatThrownBy(() -> service.project(invalid)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(evidence);
    }
    @Test void missingCloseTimestamp_doesNotFallbackToEnvelopeTime() throws Exception {
        var event = AdmissionEvidenceTestFixtures.fixture("admission.closed");
        assertThatThrownBy(() -> service.project(AdmissionEvidenceTestFixtures.changed(event, event.metadata().eventId(), "closedAt", null)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(evidence);
    }
}
