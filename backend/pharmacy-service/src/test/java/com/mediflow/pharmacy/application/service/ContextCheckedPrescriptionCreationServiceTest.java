package com.mediflow.pharmacy.application.service;

import com.mediflow.pharmacy.application.dto.command.*;
import com.mediflow.pharmacy.application.dto.request.*;
import com.mediflow.pharmacy.application.port.in.CreateCarePrescriptionWithContextUseCase;
import com.mediflow.pharmacy.application.port.out.OutpatientPrescriptionContextPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionIdentityPort;
import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import com.mediflow.pharmacy.domain.model.enums.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContextCheckedPrescriptionCreationServiceTest {
    private final OutpatientPrescriptionContextPort contexts = mock(OutpatientPrescriptionContextPort.class);
    private final CreateCarePrescriptionWithContextUseCase writer = mock(CreateCarePrescriptionWithContextUseCase.class);
    private final PrescriptionIdentityPort identities = mock(PrescriptionIdentityPort.class);
    private final Instant now = Instant.parse("2026-10-09T01:00:00Z");
    private final ContextCheckedPrescriptionCreationService service = new ContextCheckedPrescriptionCreationService(contexts, writer, Clock.fixed(now, ZoneOffset.UTC), identities);
    private final UUID record = UUID.randomUUID(), patient = UUID.randomUUID(), doctor = UUID.randomUUID(), department = UUID.randomUUID(), episode = UUID.randomUUID(), key = UUID.randomUUID();
    private final PrescriptionIdentityPort.Observation identity = new PrescriptionIdentityPort.Observation(
            patient, true, doctor, true, true, department, department, true, now);
    @org.junit.jupiter.api.BeforeEach void identity() {
        when(identities.lookup(patient, doctor, department, "context")).thenReturn(identity);
    }
    private CreatePrescriptionCommand command(String role) {
        return new CreatePrescriptionCommand(new CreatePrescriptionRequest(record, patient, doctor, department,
                LocalDate.of(2026, 10, 9), List.of(new PrescriptionLineRequest(UUID.randomUUID(), 1, "Daily")), 1,
                CareContext.OUTPATIENT, CareEpisodeType.OUTPATIENT_VISIT, episode, null, "DRUG"),
                new ActorIdentity(UUID.randomUUID(), doctor, role), "context");
    }
    private OutpatientPrescriptionContextPort.Observation observation(UUID expectedPatient) {
        return new OutpatientPrescriptionContextPort.Observation(true, record, expectedPatient, doctor, department,
                "OUTPATIENT_VISIT", episode, "OPEN", null, now);
    }
    @Test void create_freshExactClinicalProofPassedToAtomicStockWriter() {
        var command = command("DOCTOR"); var proof = observation(patient); var id = UUID.randomUUID();
        when(contexts.findRecord(record, "context")).thenReturn(proof); when(writer.createCare(key, command, proof, identity)).thenReturn(id);
        assertThat(service.createWithContext(key, command)).isEqualTo(id);
        var order = inOrder(contexts, identities, writer); order.verify(contexts).findRecord(record, "context");
        order.verify(identities).lookup(patient, doctor, department, "context"); order.verify(writer).createCare(key, command, proof, identity);
    }
    @Test void create_forbiddenActorRejectedBeforeRemoteReadOrReplay() {
        assertThatThrownBy(() -> service.createWithContext(key, command("SYSTEM")));
        verifyNoInteractions(contexts, identities, writer);
    }
    @Test void create_wrongRelationshipNeverCallsStockWriterOrOtherFallback() {
        when(contexts.findRecord(record, "context")).thenReturn(observation(UUID.randomUUID()));
        assertThatThrownBy(() -> service.createWithContext(key, command("DOCTOR"))).isInstanceOf(com.mediflow.common.exception.BusinessRuleException.class);
        verifyNoInteractions(writer);
    }
    @Test void create_upstreamFailureDoesNotReturnUnverifiedPrescription() {
        when(contexts.findRecord(record, "context")).thenThrow(new PharmacyUpstreamUnavailableException());
        assertThatThrownBy(() -> service.createWithContext(key, command("DOCTOR"))).isInstanceOf(PharmacyUpstreamUnavailableException.class);
        verifyNoInteractions(writer);
    }
    @Test void create_everyReceiptReplayRequiresNewContextRead() {
        var command = command("DOCTOR"); var proof = observation(patient);
        when(contexts.findRecord(record, "context")).thenReturn(proof).thenThrow(new PharmacyUpstreamUnavailableException());
        when(writer.createCare(key, command, proof, identity)).thenReturn(UUID.randomUUID());
        service.createWithContext(key, command);
        assertThatThrownBy(() -> service.createWithContext(key, command)).isInstanceOf(PharmacyUpstreamUnavailableException.class);
        verify(writer, times(1)).createCare(key, command, proof, identity);
    }
    @Test void create_ineligibleCurrentDoctorDeniesBeforeStockAndReplayEvenForAdmin() {
        var command = command("ADMIN"); when(contexts.findRecord(record, "context")).thenReturn(observation(patient));
        when(identities.lookup(patient, doctor, department, "context")).thenReturn(new PrescriptionIdentityPort.Observation(
                patient, true, doctor, true, false, null, department, true, now));
        assertThatThrownBy(() -> service.createWithContext(key, command)).isInstanceOf(com.mediflow.common.exception.BusinessRuleException.class);
        verifyNoInteractions(writer);
    }
    @Test void create_unavailableIdentityNeverUsesContextOnlyFallback() {
        when(contexts.findRecord(record, "context")).thenReturn(observation(patient));
        when(identities.lookup(patient, doctor, department, "context")).thenThrow(new PharmacyUpstreamUnavailableException());
        assertThatThrownBy(() -> service.createWithContext(key, command("DOCTOR"))).isInstanceOf(PharmacyUpstreamUnavailableException.class);
        verifyNoInteractions(writer);
    }
}
