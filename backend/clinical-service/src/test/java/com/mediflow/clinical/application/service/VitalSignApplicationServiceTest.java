package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import com.mediflow.clinical.application.dto.request.VitalSignCreateRequest;
import com.mediflow.clinical.application.dto.response.VitalSignObservationDTO;
import com.mediflow.clinical.application.port.out.ClinicQueueRepositoryPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.port.out.VitalSignRepositoryPort;
import com.mediflow.clinical.domain.model.ConsultationSession;
import com.mediflow.clinical.domain.model.OutpatientReceptionMode;
import com.mediflow.clinical.domain.model.OutpatientRegistration;
import com.mediflow.clinical.domain.model.VitalSignObservation;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VitalSignApplicationServiceTest {
    private final IntakeHarness h = new IntakeHarness();
    private final VitalSignRepositoryPort vitals = mock(VitalSignRepositoryPort.class);
    private final VitalSignCreateRequest full = new VitalSignCreateRequest(80, 120, 80, new BigDecimal("37.0"), 18, 98, new BigDecimal("60"), null);
    private ConsultationSession session;
    private OutpatientRegistration registration;

    private VitalSignApplicationService service() {
        var queue = new ClinicQueueApplicationService(h.sessions, h.registrations, mock(ClinicQueueRepositoryPort.class), h.authority, h.actors,
                h.correlationIds, h.idempotency, h.clock);
        return new VitalSignApplicationService(h.sessions, h.registrations, vitals, queue, h.registrationService(true), h.actors, h.idempotency, h.clock);
    }

    @BeforeEach void setUp() {
        h.actAsNurse();
        session = h.session();
        registration = h.stub(h.arrivedConfirmed(session));
        lenient().when(vitals.insert(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test void onlyDepartmentNurseRecords() {
        h.actAsDoctor();
        assertThatThrownBy(() -> service().record(registration.registrationId(), full, "key"))
                .isInstanceOfSatisfying(ForbiddenOperationException.class, e -> assertThat(e.getCode()).isEqualTo("CLINIC_DESK_SCOPE_DENIED"));
        verify(vitals, never()).insert(any());
    }

    @Test void receptionNurseCannotRecord() {
        h.actAs("NURSE", UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> service().record(registration.registrationId(), full, "key")).isInstanceOf(ForbiddenOperationException.class);
        verify(vitals, never()).insert(any());
    }

    @Test void requiresArrivedRegistration() {
        var notArrived = h.stub(h.confirmed(session, OutpatientReceptionMode.PREBOOKED));
        assertThatThrownBy(() -> service().record(notArrived.registrationId(), full, "key")).isInstanceOf(BusinessRuleException.class);
        verify(vitals, never()).insert(any());
    }

    @Test void invalidMeasurementIsReportedWithItsCode() {
        var bad = new VitalSignCreateRequest(10, 120, 80, new BigDecimal("37.0"), 18, 98, new BigDecimal("60"), null);
        assertThatThrownBy(() -> service().record(registration.registrationId(), bad, "key"))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getCode()).isEqualTo("VITAL_SIGNS_INVALID"));
    }

    @Test void recordingAgainAddsNewRowAndKeepsOld() {
        var service = service();
        var first = service.record(registration.registrationId(), full, "a").data();
        var second = service.record(registration.registrationId(), full, "b").data();
        assertThat(second.observationId()).isNotEqualTo(first.observationId());
        verify(vitals, times(2)).insert(any());
    }

    @Test void assignedDoctorCanRead() {
        h.actAsDoctor();
        var stored = VitalSignObservation.record(UUID.randomUUID(), registration.registrationId(), registration.patientId(), h.departmentId,
                IntakeHarness.NOW, UUID.randomUUID(), null, 80, 120, 80, new BigDecimal("37.0"), 18, 98, new BigDecimal("60"), null);
        when(vitals.listByRegistration(registration.registrationId())).thenReturn(List.of(stored));
        assertThat(service().list(registration.registrationId())).extracting(VitalSignObservationDTO::observationId).containsExactly(stored.observationId());
    }

    @Test void otherDoctorGetsNotFound() {
        h.actAs("DOCTOR", UUID.randomUUID(), h.departmentId);
        assertThatThrownBy(() -> service().list(registration.registrationId())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void sameKeyReplaysSameObservation() {
        Map<String, Object> results = new HashMap<>();
        doAnswer(call -> new IdempotentCommandPort.Result<>(
                results.computeIfAbsent(call.getArgument(3), ignored -> ((Supplier<?>) call.getArgument(8)).get()),
                call.getArgument(6), null, false))
                .when(h.idempotency).execute(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyInt(), any(), any());
        var service = service();
        var first = service.record(registration.registrationId(), full, "key").data();
        assertThat(service.record(registration.registrationId(), full, "key").data()).isEqualTo(first);
        verify(vitals, times(1)).insert(any());
    }

    @Test void noAbnormalFlagInResponse() {
        assertThat(Arrays.stream(VitalSignObservationDTO.class.getRecordComponents()).map(c -> c.getName().toLowerCase()))
                .noneMatch(name -> name.contains("abnormal") || name.contains("flag") || name.contains("assessment") || name.contains("severity"));
    }
}
