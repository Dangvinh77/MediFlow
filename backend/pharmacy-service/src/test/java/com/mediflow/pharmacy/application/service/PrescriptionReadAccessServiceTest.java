package com.mediflow.pharmacy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.dto.response.PrescriptionDTO;
import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import com.mediflow.pharmacy.application.port.in.GetPrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.SearchPrescriptionsUseCase;
import com.mediflow.pharmacy.application.port.out.ClinicalReadDecisionPort;
import com.mediflow.pharmacy.application.port.out.ClinicalReadDecisionPort.ClinicalReadDecision;

class PrescriptionReadAccessServiceTest {
    private static final PageQuery PAGE = PageQuery.of(0, 20);
    private static final String CORRELATION = "corr-1";

    private final GetPrescriptionUseCase prescriptions = mock(GetPrescriptionUseCase.class);
    private final SearchPrescriptionsUseCase searches = mock(SearchPrescriptionsUseCase.class);
    private final ClinicalReadDecisionPort decisions = mock(ClinicalReadDecisionPort.class);
    private final PrescriptionReadAccessService service =
            new PrescriptionReadAccessService(prescriptions, searches, decisions, true);

    private final UUID prescriptionId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final ActorIdentity doctor = new ActorIdentity(UUID.randomUUID(), staffId, "DOCTOR");
    private final ActorIdentity pharmacist = new ActorIdentity(UUID.randomUUID(), staffId, "PHARMACIST");
    private final ActorIdentity admin = new ActorIdentity(UUID.randomUUID(), null, "ADMIN");

    @Test
    void pharmacistKeepsWorkAccess() {
        PrescriptionDTO prescription = prescription();
        when(prescriptions.getPrescriptionById(prescriptionId)).thenReturn(prescription);
        PageResult<PrescriptionDTO> page = PageResult.empty(PAGE);
        when(searches.search(null, patientId, null, PAGE)).thenReturn(page);

        assertThat(service.getById(prescriptionId, pharmacist, CORRELATION)).isSameAs(prescription);
        assertThat(service.search(null, patientId, null, PAGE, pharmacist, CORRELATION)).isSameAs(page);
        verifyNoInteractions(decisions);
    }

    @Test
    void doctorDeniedOnGetById() {
        when(prescriptions.getPrescriptionById(prescriptionId)).thenReturn(prescription());
        when(decisions.decide(patientId, doctor, CORRELATION))
                .thenReturn(new ClinicalReadDecision(false, "RECORD_ACCESS_PURPOSE_REQUIRED"));

        assertThatThrownBy(() -> service.getById(prescriptionId, doctor, CORRELATION))
                .isInstanceOf(ForbiddenOperationException.class)
                .extracting("code").isEqualTo("RECORD_ACCESS_PURPOSE_REQUIRED");
    }

    @Test
    void doctorAllowedOnGetByIdReceivesPrescription() {
        PrescriptionDTO prescription = prescription();
        when(prescriptions.getPrescriptionById(prescriptionId)).thenReturn(prescription);
        when(decisions.decide(patientId, doctor, CORRELATION)).thenReturn(new ClinicalReadDecision(true, null));

        assertThat(service.getById(prescriptionId, doctor, CORRELATION)).isSameAs(prescription);
    }

    @Test
    void searchWithPatientChecksDecision() {
        when(decisions.decide(patientId, doctor, CORRELATION)).thenReturn(new ClinicalReadDecision(false, null));

        assertThatThrownBy(() -> service.search("thuoc", patientId, "ACTIVE", PAGE, doctor, CORRELATION))
                .isInstanceOf(ForbiddenOperationException.class)
                .extracting("code").isEqualTo("RECORD_ACCESS_DENIED");
        verifyNoInteractions(searches);
    }

    @Test
    void doctorSearchWithoutPatientIsLimitedToOwnPrescriptions() {
        PageResult<PrescriptionDTO> page = PageResult.empty(PAGE);
        when(searches.search("thuoc", null, staffId, "ACTIVE", PAGE)).thenReturn(page);

        assertThat(service.search("thuoc", null, "ACTIVE", PAGE, doctor, CORRELATION)).isSameAs(page);
        verify(searches).search("thuoc", null, staffId, "ACTIVE", PAGE);
        verifyNoInteractions(decisions);
    }

    @Test
    void doctorWithoutStaffIdCannotSearchAcrossPatients() {
        ActorIdentity noStaff = new ActorIdentity(UUID.randomUUID(), null, "DOCTOR");

        assertThatThrownBy(() -> service.search(null, null, null, PAGE, noStaff, CORRELATION))
                .isInstanceOf(ForbiddenOperationException.class)
                .extracting("code").isEqualTo("RECORD_ACCESS_DENIED");
        verifyNoInteractions(searches);
    }

    @Test
    void adminSearchWithoutPatientStaysUnfiltered() {
        PageResult<PrescriptionDTO> page = PageResult.empty(PAGE);
        when(searches.search(null, null, null, PAGE)).thenReturn(page);

        assertThat(service.search(null, null, null, PAGE, admin, CORRELATION)).isSameAs(page);
        verifyNoInteractions(decisions);
    }

    @Test
    void clinicalTimeoutFailsClosed() {
        when(decisions.decide(eq(patientId), eq(doctor), any())).thenThrow(new PharmacyUpstreamUnavailableException());

        assertThatThrownBy(() -> service.search(null, patientId, null, PAGE, doctor, CORRELATION))
                .isInstanceOf(PharmacyUpstreamUnavailableException.class);
        verifyNoInteractions(searches);
    }

    @Test
    void missingActorIsDeniedWhenPolicyIsOn() {
        assertThatThrownBy(() -> service.search(null, patientId, null, PAGE, null, CORRELATION))
                .isInstanceOf(ForbiddenOperationException.class)
                .extracting("code").isEqualTo("RECORD_ACCESS_DENIED");
        verifyNoInteractions(searches, decisions);
    }

    @Test
    void flagOffKeepsLegacyBehaviour() {
        PrescriptionReadAccessService legacy =
                new PrescriptionReadAccessService(prescriptions, searches, null, false);
        PrescriptionDTO prescription = prescription();
        when(prescriptions.getPrescriptionById(prescriptionId)).thenReturn(prescription);
        PageResult<PrescriptionDTO> page = PageResult.empty(PAGE);
        when(searches.search(null, null, null, PAGE)).thenReturn(page);

        assertThat(legacy.getById(prescriptionId, null, CORRELATION)).isSameAs(prescription);
        assertThat(legacy.search(null, null, null, PAGE, doctor, CORRELATION)).isSameAs(page);
    }

    private PrescriptionDTO prescription() {
        return new PrescriptionDTO(prescriptionId, null, patientId, UUID.randomUUID(), null, null, null, List.of(),
                null, null, false, null, null, null, null, null);
    }
}
