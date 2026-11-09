package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.mediflow.clinical.application.port.out.OutpatientMvpMode;
import com.mediflow.clinical.application.port.out.RecordAccessPolicyMode;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.request.RecordAccessDeclarationRequest;
import com.mediflow.clinical.application.exception.InvalidRecordAccessDeclarationException;
import com.mediflow.clinical.application.port.out.AppointmentRepositoryPort;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.RecordAccessDeclarationPort;
import com.mediflow.clinical.application.port.out.RecordAccessLogPort;

class RecordAccessDeclarationValidationTest {
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void missingPurposeIsAnInvalidRequest(String purpose) {
        CurrentClinicalActorPort actors = mock(CurrentClinicalActorPort.class);
        when(actors.current()).thenReturn(new ActorIdentity(UUID.randomUUID(), UUID.randomUUID(), null, "DOCTOR"));
        RecordAccessApplicationService service = new RecordAccessApplicationService(
                mock(MedicalRecordRepositoryPort.class), mock(AppointmentRepositoryPort.class),
                mock(RecordAccessDeclarationPort.class), mock(RecordAccessLogPort.class), mock(RecordReadPolicy.class),
                actors, mock(CorrelationIdProvider.class), mock(OutpatientMvpMode.class), mock(RecordAccessPolicyMode.class));

        assertThatThrownBy(() -> service.declare(new RecordAccessDeclarationRequest(UUID.randomUUID(), purpose, null)))
                .isInstanceOf(InvalidRecordAccessDeclarationException.class);
    }
}
