package com.mediflow.pharmacy.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static com.mediflow.pharmacy.support.ClearanceTestFixtures.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Clock;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.mediflow.pharmacy.application.dto.command.PrescriptionClearanceCommand;
import com.mediflow.pharmacy.application.port.out.PrescriptionClearancePort;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;

class PrescriptionClearanceApplicationServiceTest {
    private final PrescriptionClearancePort clearances = mock(PrescriptionClearancePort.class);
    private final PrescriptionRepositoryPort prescriptions = mock(PrescriptionRepositoryPort.class);
    private final PrescriptionClearanceApplicationService service =
            new PrescriptionClearanceApplicationService(prescriptions, clearances);

    @Test
    void project_beforePrescription_storesPendingWithoutAnyDispensePort() {
        var grant = grant();
        var command = new PrescriptionClearanceCommand(UUID.randomUUID(), "b".repeat(64), grant);
        when(clearances.claim(command.eventId(), command.eventFingerprint(), grant)).thenReturn(true);
        when(prescriptions.findByIdForUpdate(grant.prescriptionId())).thenReturn(Optional.empty());
        service.project(command);
        var order = inOrder(prescriptions, clearances);
        order.verify(clearances).claim(command.eventId(), command.eventFingerprint(), grant);
        order.verify(prescriptions).findByIdForUpdate(grant.prescriptionId());
        order.verify(clearances).lockTarget(grant.prescriptionId());
        order.verify(clearances).store(grant, false);
    }

    @Test
    void project_wrongKnownPatient_doesNotStore() {
        var grant = grant();
        var command = new PrescriptionClearanceCommand(UUID.randomUUID(), "b".repeat(64), grant);
        when(clearances.claim(command.eventId(), command.eventFingerprint(), grant)).thenReturn(true);
        when(prescriptions.findByIdForUpdate(grant.prescriptionId()))
                .thenReturn(Optional.of(prescription(withPatient(grant, UUID.randomUUID()))));
        assertThatThrownBy(() -> service.project(command)).hasMessageContaining("exact V1");
        verify(clearances, never()).store(any(), anyBoolean());
    }

    @Test
    void project_duplicateDelivery_doesNotApplyAgain() {
        var command = new PrescriptionClearanceCommand(UUID.randomUUID(), "b".repeat(64), grant());
        service.project(command);
        verifyNoInteractions(prescriptions);
        verify(clearances, never()).store(any(), anyBoolean());
    }

    @Test
    void authorize_expiryReachedWhileWaitingForLock_deniesWithoutVerifying() {
        var grant = grant();
        var clock = mock(Clock.class);
        when(clock.instant()).thenReturn(GRANTED_AT);
        doAnswer(invocation -> { when(clock.instant()).thenReturn(EXPIRES_AT); return null; })
                .when(clearances).lockTarget(grant.prescriptionId());
        when(clearances.findByTargetForUpdate(grant.prescriptionId())).thenReturn(List.of(grant));
        var authorizer = new PrescriptionClearanceAuthorizationService(clearances, clock);
        assertThatThrownBy(() -> authorizer.requireValidAfterLocks(prescription(grant)))
                .hasMessageContaining("non-expired");
        verify(clearances, never()).markVerified(any());
    }

    @Test
    void authorize_matchingEarlyGrant_verifiesItWithoutUsingLegacyReceipt() {
        var grant = grant();
        when(clearances.findByTargetForUpdate(grant.prescriptionId())).thenReturn(List.of(grant));
        new PrescriptionClearanceAuthorizationService(clearances, Clock.fixed(GRANTED_AT, ZoneOffset.UTC))
                .requireValidAfterLocks(prescription(grant));
        verify(clearances).markVerified(grant.clearanceId());
    }

    @Test
    void authorize_cancelledPrescription_doesNotGrantPermission() {
        var grant = grant();
        var prescription = prescription(grant);
        prescription.cancel(UUID.randomUUID(), "Cancelled", GRANTED_AT);
        var authorizer = new PrescriptionClearanceAuthorizationService(clearances,
                Clock.fixed(GRANTED_AT, ZoneOffset.UTC));
        assertThatThrownBy(() -> authorizer.requireValidAfterLocks(prescription)).hasMessageContaining("terminal");
        verifyNoInteractions(clearances);
    }
}
