package com.mediflow.lab.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.lab.application.dto.response.LabTestDTO;
import com.mediflow.lab.application.exception.LabUpstreamUnavailableException;
import com.mediflow.lab.application.port.in.ManageLabTestUseCase;
import com.mediflow.lab.application.port.out.ClinicalReadDecisionPort;
import com.mediflow.lab.application.port.out.ClinicalReadDecisionPort.ClinicalReadDecision;
import com.mediflow.lab.application.port.out.CorrelationIdProvider;
import com.mediflow.lab.application.port.out.CurrentReaderPort;
import com.mediflow.lab.application.port.out.ReaderIdentity;
import com.mediflow.lab.domain.model.CareEpisodeType;

class LabReadAccessServiceTest {
    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID TEST_ID = UUID.randomUUID();
    private static final UUID OWN_DEPARTMENT = UUID.randomUUID();
    private static final UUID OTHER_DEPARTMENT = UUID.randomUUID();
    private static final UUID CORRELATION = UUID.randomUUID();
    private static final PageQuery PAGE = PageQuery.of(0, 20);
    private static final ClinicalReadDecision ALLOWED = new ClinicalReadDecision(true, null);
    private static final ClinicalReadDecision NEEDS_PURPOSE =
            new ClinicalReadDecision(false, "RECORD_ACCESS_PURPOSE_REQUIRED");

    private final ManageLabTestUseCase labs = mock(ManageLabTestUseCase.class);
    private final ClinicalReadDecisionPort decisions = mock(ClinicalReadDecisionPort.class);
    private final CurrentReaderPort readers = mock(CurrentReaderPort.class);
    private final CorrelationIdProvider correlations = mock(CorrelationIdProvider.class);
    private final LabTestDTO test = mock(LabTestDTO.class);

    private LabReadAccessService service(boolean enabled) {
        return new LabReadAccessService(labs, decisions, readers, correlations, enabled);
    }

    @BeforeEach
    void setUp() {
        when(correlations.currentOrCreate()).thenReturn(CORRELATION);
        when(test.patientId()).thenReturn(PATIENT);
        when(labs.getById(TEST_ID)).thenReturn(test);
        when(labs.byPatient(PATIENT)).thenReturn(List.of(test));
    }

    @Test
    void labTechKeepsWorkQueueAccess() {
        reader("LAB_TECH", OWN_DEPARTMENT);

        assertThat(service(true).getById(TEST_ID)).isSameAs(test);
        service(true).search(null, null, null, null, PAGE);

        verify(labs).search(null, null, null, null, PAGE);
        verifyNoInteractions(decisions);
    }

    @Test
    void doctorWithoutBasisDeniedOnGetById() {
        ReaderIdentity reader = reader("DOCTOR", OWN_DEPARTMENT);
        when(decisions.decide(PATIENT, reader, CORRELATION.toString())).thenReturn(NEEDS_PURPOSE);

        assertThatThrownBy(() -> service(true).getById(TEST_ID))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("code", "RECORD_ACCESS_PURPOSE_REQUIRED");
    }

    @Test
    void doctorWithBasisReadsById() {
        ReaderIdentity reader = reader("DOCTOR", OWN_DEPARTMENT);
        when(decisions.decide(PATIENT, reader, CORRELATION.toString())).thenReturn(ALLOWED);

        assertThat(service(true).getById(TEST_ID)).isSameAs(test);
    }

    @Test
    void doctorDeniedOnByPatient() {
        ReaderIdentity reader = reader("DOCTOR", OWN_DEPARTMENT);
        when(decisions.decide(PATIENT, reader, CORRELATION.toString())).thenReturn(NEEDS_PURPOSE);

        assertThatThrownBy(() -> service(true).byPatient(PATIENT))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("code", "RECORD_ACCESS_PURPOSE_REQUIRED");
        verify(labs, never()).byPatient(any());
    }

    @Test
    void episodeSearchChecksPatientOfResults() {
        ReaderIdentity reader = reader("DOCTOR", OWN_DEPARTMENT);
        UUID episode = UUID.randomUUID();
        when(labs.search(null, null, CareEpisodeType.OUTPATIENT_VISIT, episode, PAGE))
                .thenReturn(PageResult.of(List.of(test, test), 2, 0, 20));
        when(decisions.decide(PATIENT, reader, CORRELATION.toString())).thenReturn(NEEDS_PURPOSE);

        assertThatThrownBy(() -> service(true).search(null, null, CareEpisodeType.OUTPATIENT_VISIT, episode, PAGE))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("code", "RECORD_ACCESS_PURPOSE_REQUIRED");
    }

    @Test
    void unfilteredSearchForDoctorIsForcedToOwnDepartment() {
        reader("DOCTOR", OWN_DEPARTMENT);

        service(true).search(OTHER_DEPARTMENT, null, null, null, PAGE);

        verify(labs).search(eq(OWN_DEPARTMENT), any(), any(), any(), eq(PAGE));
        verifyNoInteractions(decisions);
    }

    @Test
    void unfilteredSearchForNurseIsForcedToOwnDepartment() {
        reader("NURSE", OWN_DEPARTMENT);

        service(true).search(null, null, null, null, PAGE);

        verify(labs).search(eq(OWN_DEPARTMENT), any(), any(), any(), eq(PAGE));
    }

    @Test
    void unfilteredSearchWithoutDepartmentClaimIsDenied() {
        reader("DOCTOR", null);

        assertThatThrownBy(() -> service(true).search(OTHER_DEPARTMENT, null, null, null, PAGE))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("code", "RECORD_ACCESS_DENIED");
    }

    @Test
    void missingReaderIsDenied() {
        when(readers.current()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(true).getById(TEST_ID))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("code", "RECORD_ACCESS_DENIED");
    }

    @Test
    void clinicalTimeoutFailsClosed() {
        ReaderIdentity reader = reader("DOCTOR", OWN_DEPARTMENT);
        when(decisions.decide(PATIENT, reader, CORRELATION.toString())).thenThrow(new LabUpstreamUnavailableException());

        assertThatThrownBy(() -> service(true).getById(TEST_ID)).isInstanceOf(LabUpstreamUnavailableException.class);
    }

    @Test
    void flagOffKeepsLegacyBehaviour() {
        assertThat(service(false).getById(TEST_ID)).isSameAs(test);
        service(false).search(OTHER_DEPARTMENT, null, null, null, PAGE);

        verify(labs).search(OTHER_DEPARTMENT, null, null, null, PAGE);
        verifyNoInteractions(decisions, readers);
    }

    private ReaderIdentity reader(String role, UUID department) {
        ReaderIdentity reader = new ReaderIdentity(UUID.randomUUID(), UUID.randomUUID(), role, department);
        when(readers.current()).thenReturn(Optional.of(reader));
        return reader;
    }
}
