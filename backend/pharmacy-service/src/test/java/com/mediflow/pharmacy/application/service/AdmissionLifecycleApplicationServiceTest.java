package com.mediflow.pharmacy.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.pharmacy.application.dto.command.AdmissionLifecycleCommand;
import com.mediflow.pharmacy.application.port.out.AdmissionMedicationContextPort;
import com.mediflow.pharmacy.domain.model.AdmissionLifecycleFact;
import com.mediflow.pharmacy.domain.model.AdmissionMedicationContext;

class AdmissionLifecycleApplicationServiceTest {
    private final AdmissionMedicationContextPort contexts = mock(AdmissionMedicationContextPort.class);
    private final AdmissionLifecycleApplicationService service = new AdmissionLifecycleApplicationService(contexts);
    private final AdmissionLifecycleFact fact = new AdmissionLifecycleFact(AdmissionLifecycleFact.Kind.CLOSED,
            UUID.randomUUID(), UUID.randomUUID(), null, Instant.now(), "a".repeat(64));
    private final AdmissionLifecycleCommand command = new AdmissionLifecycleCommand(UUID.randomUUID(), "b".repeat(64), fact);

    @Test
    void duplicateEventDoesNotTouchContext() {
        service.project(command);
        verify(contexts).claim(command.eventId(), command.eventFingerprint());
        verifyNoMoreInteractions(contexts);
    }

    @Test
    void claimLockAndSaveUseExactAdmissionIdentity() {
        when(contexts.claim(any(), any())).thenReturn(true);
        var seed = AdmissionMedicationContext.empty(fact.admissionId(), fact.patientId());
        when(contexts.lockOrCreate(fact.admissionId(), fact.patientId())).thenReturn(seed);
        service.project(command);
        var order = inOrder(contexts);
        order.verify(contexts).claim(command.eventId(), command.eventFingerprint());
        order.verify(contexts).lockOrCreate(fact.admissionId(), fact.patientId());
        order.verify(contexts).save(seed.apply(fact));
    }
}
