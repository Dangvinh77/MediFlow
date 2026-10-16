package com.mediflow.pharmacy.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.dto.command.CancelPrescriptionCommand;
import com.mediflow.pharmacy.application.dto.response.DispenseDTO;
import com.mediflow.pharmacy.application.port.in.*;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;
import com.mediflow.pharmacy.domain.exception.DispenseAuthorizationException;
import com.mediflow.pharmacy.domain.exception.DrugRuleException;
import com.mediflow.pharmacy.domain.exception.StockReservationRuleException;
import com.mediflow.pharmacy.domain.model.*;
import com.mediflow.pharmacy.domain.model.enums.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OutpatientPrescriptionLifecycleApplicationServiceTest {
    private final PrescriptionRepositoryPort prescriptions = mock(PrescriptionRepositoryPort.class);
    private final DispenseCarePrescriptionUseCase dispense = mock(DispenseCarePrescriptionUseCase.class);
    private final RecordCareStockFailureUseCase failures = mock(RecordCareStockFailureUseCase.class);
    private final CancelCarePrescriptionUseCase cancel = mock(CancelCarePrescriptionUseCase.class);
    private final ExpireCarePrescriptionUseCase expiry = mock(ExpireCarePrescriptionUseCase.class);
    private final OutpatientPrescriptionLifecycleApplicationService service =
            new OutpatientPrescriptionLifecycleApplicationService(prescriptions, dispense, failures, cancel, expiry);
    private final UUID id = UUID.randomUUID();
    private final ActorIdentity pharmacist = new ActorIdentity(UUID.randomUUID(), UUID.randomUUID(), "PHARMACIST");

    @Test
    void manualV1DispenseUsesSignedStaffAndNoPaymentReceipt() {
        outpatient();
        var expected = dto(DispenseStatus.DISPENSED);
        when(dispense.execute(id, DispenseActor.staff(pharmacist.staffId()), "trace")).thenReturn(expected);
        assertThat(service.dispense(id, pharmacist, " trace ")).isSameAs(expected);
        verifyNoInteractions(failures, cancel, expiry);
    }

    @Test
    void adminUsesTypedAccountIdentityRatherThanFabricatedStaff() {
        outpatient();
        ActorIdentity admin = new ActorIdentity(UUID.randomUUID(), null, "ADMIN");
        service.dispense(id, admin, "trace");
        verify(dispense).execute(id, DispenseActor.account(admin.accountId()), "trace");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SYSTEM", "DOCTOR", "PATIENT"})
    void wrongActorCannotStartLookupOrAnyStockEffect(String role) {
        assertThatThrownBy(() -> service.dispense(id,
                new ActorIdentity(UUID.randomUUID(), UUID.randomUUID(), role), "trace"))
                .isInstanceOf(DispenseAuthorizationException.class);
        verifyNoInteractions(prescriptions, dispense, failures);
    }

    @Test
    void missingSignedStaffCannotUseAccountAsPharmacist() {
        assertThatThrownBy(() -> service.dispense(id,
                new ActorIdentity(UUID.randomUUID(), null, "PHARMACIST"), "trace"))
                .isInstanceOf(DispenseAuthorizationException.class);
        verifyNoInteractions(prescriptions, dispense, failures);
    }

    @Test
    void persistedLegacyCannotBeReinterpretedAsOutpatientV1() {
        when(prescriptions.findById(id)).thenReturn(Optional.of(prescription(PrescriptionCareContext.legacy())));
        assertThatThrownBy(() -> service.dispense(id, pharmacist, "trace"))
                .hasMessageContaining("persisted V1 outpatient");
        verifyNoInteractions(dispense, failures);
    }

    @Test
    void persistedAdmissionCannotBeDispensedCancelledOrExpiredByMvp() {
        UUID admission = UUID.randomUUID();
        var context = PrescriptionCareContext.v1(CareContext.ADMISSION,
                new CareEpisode(CareEpisodeType.ADMISSION, admission), admission, "RX");
        when(prescriptions.findById(id)).thenReturn(Optional.of(prescription(context)));
        assertThatThrownBy(() -> service.dispense(id, pharmacist, "trace"));
        assertThatThrownBy(() -> service.cancel(new CancelPrescriptionCommand(id,
                new ActorIdentity(UUID.randomUUID(), null, "ADMIN"), "Reason", "trace")));
        assertThatThrownBy(() -> service.expire(id, "trace"));
        verifyNoInteractions(dispense, failures, cancel, expiry);
    }

    @Test
    void clearanceDenialIsNotRecordedAsStockFailure() {
        outpatient();
        var denial = new DispenseAuthorizationException("PHARMACY_CLEARANCE_REQUIRED", "No grant");
        when(dispense.execute(eq(id), any(), anyString())).thenThrow(denial);
        assertThatThrownBy(() -> service.dispense(id, pharmacist, "trace")).isSameAs(denial);
        verifyNoInteractions(failures);
    }

    @Test
    void evidenceCorruptionIsNotRecordedAsStockFailure() {
        outpatient();
        var denial = new StockReservationRuleException("RESERVATION_MISMATCH", "Corrupt reservations");
        when(dispense.execute(eq(id), any(), anyString())).thenThrow(denial);
        assertThatThrownBy(() -> service.dispense(id, pharmacist, "trace")).isSameAs(denial);
        verifyNoInteractions(failures);
    }

    @Test
    void infrastructureFailureIsNotConvertedIntoBusinessFailure() {
        outpatient();
        var error = new IllegalStateException("Storage unavailable");
        when(dispense.execute(eq(id), any(), anyString())).thenThrow(error);
        assertThatThrownBy(() -> service.dispense(id, pharmacist, "trace")).isSameAs(error);
        verifyNoInteractions(failures);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRUG_OUT_OF_STOCK", "DRUG_EXPIRED", "RESERVATION_EXPIRED"})
    void stockFailureReturnsOnlyReevaluatedPersistedOutcome(String code) {
        outpatient();
        when(dispense.execute(eq(id), any(), anyString())).thenThrow(new DrugRuleException(code, "Stock failure"));
        var expected = dto(DispenseStatus.FAILED);
        when(failures.recordStockFailure(eq(id), any(), eq("trace"))).thenReturn(Optional.of(expected));
        assertThat(service.dispense(id, pharmacist, "trace")).isSameAs(expected);
        var order = inOrder(dispense, failures);
        order.verify(dispense).execute(id, DispenseActor.staff(pharmacist.staffId()), "trace");
        order.verify(failures).recordStockFailure(id, DispenseActor.staff(pharmacist.staffId()), "trace");
    }

    @Test
    void recoveredStockOrLostTerminalRaceDoesNotInventFailedOutcome() {
        outpatient();
        var original = new DrugRuleException("DRUG_OUT_OF_STOCK", "Original failure");
        when(dispense.execute(eq(id), any(), anyString())).thenThrow(original);
        when(failures.recordStockFailure(eq(id), any(), anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.dispense(id, pharmacist, "trace")).isSameAs(original);
    }

    @Test
    void failureReevaluationDenialCannotFallBackToUnprovenFailure() {
        outpatient();
        when(dispense.execute(eq(id), any(), anyString())).thenThrow(new DrugRuleException("DRUG_OUT_OF_STOCK", "Stock"));
        var denial = new DispenseAuthorizationException("PHARMACY_CLEARANCE_REQUIRED", "Grant expired");
        when(failures.recordStockFailure(eq(id), any(), anyString())).thenThrow(denial);
        assertThatThrownBy(() -> service.dispense(id, pharmacist, "trace")).isSameAs(denial);
    }

    @Test
    void cancelAndExpiryUseTheirV1TransactionPorts() {
        outpatient();
        var command = new CancelPrescriptionCommand(id,
                new ActorIdentity(UUID.randomUUID(), null, "ADMIN"), "Reason", "trace");
        service.cancel(command);
        verify(cancel).cancelCare(command);
        when(expiry.expireCare(id, "trace")).thenReturn(2);
        assertThat(service.expire(id, "trace")).isEqualTo(2);
        verifyNoInteractions(dispense, failures);
    }

    private void outpatient() {
        when(prescriptions.findById(id)).thenReturn(Optional.of(prescription(PrescriptionCareContext.v1(
                CareContext.OUTPATIENT, new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID()), null, "RX"))));
    }

    private Prescription prescription(PrescriptionCareContext context) {
        Instant now = Instant.parse("2026-10-14T01:00:00Z");
        var line = PrescriptionLine.create(UUID.randomUUID(), 1, new BigDecimal("10.00"), "Daily", "Stored name");
        return Prescription.restore(id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 10, 14), new BigDecimal("10.00"), List.of(line), PrescriptionStatus.ACTIVE,
                null, null, null, now, now, context);
    }

    private DispenseDTO dto(DispenseStatus status) {
        return new DispenseDTO(UUID.randomUUID(), id, status, null, null, null, "Reason");
    }
}
