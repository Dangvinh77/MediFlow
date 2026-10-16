package com.mediflow.pharmacy.application.service;

import static com.mediflow.pharmacy.support.ClearanceTestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mediflow.pharmacy.application.port.out.PrescriptionClearanceAuthorityPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionClearanceAuthorityPort.Observation;
import com.mediflow.pharmacy.application.port.out.PrescriptionClearancePort;
import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import com.mediflow.pharmacy.domain.exception.DispenseAuthorizationException;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PrescriptionCurrentAuthorityTest {
    @ParameterizedTest @ValueSource(strings={"eligible","missing","invoice","account","patient","purpose","episode","admission","surgery","expiry","stale","future"})
    void deniesRevokedMissingMismatchedOrStaleCurrentAuthorityWithoutMarkingLocalGrant(String mutation) {
        var grant=grant(); Instant now=GRANTED_AT.plusSeconds(1);
        var local=mock(PrescriptionClearancePort.class); var remote=mock(PrescriptionClearanceAuthorityPort.class);
        when(local.findByTargetForUpdate(grant.prescriptionId())).thenReturn(List.of(grant));
        var proof=new Observation(!mutation.equals("missing"),grant.clearanceId(),!mutation.equals("eligible"),
                mutation.equals("invoice")?UUID.randomUUID():grant.invoiceId(),mutation.equals("account")?UUID.randomUUID():grant.accountId(),
                mutation.equals("patient")?UUID.randomUUID():grant.patientId(),mutation.equals("purpose")?"EXAM":"PRESCRIPTION",
                "OUTPATIENT_VISIT",mutation.equals("episode")?UUID.randomUUID():grant.episode().id(),
                mutation.equals("admission")?UUID.randomUUID():null,mutation.equals("surgery")?UUID.randomUUID():null,
                grant.grantedAt(),mutation.equals("expiry")?grant.expiresAt().plusSeconds(1):grant.expiresAt(),
                mutation.equals("stale")?now.minusSeconds(6):mutation.equals("future")?now.plusSeconds(3):now);
        when(remote.lookup(grant.clearanceId(),"trace")).thenReturn(proof);
        var service=new PrescriptionClearanceAuthorizationService(local,Clock.fixed(now,ZoneOffset.UTC),Optional.of(remote),true);
        assertThatThrownBy(()->service.requireValidAfterLocks(prescription(grant),"trace"))
                .isInstanceOfAny(DispenseAuthorizationException.class,PharmacyUpstreamUnavailableException.class);
        verify(local,never()).markVerified(any());
    }
    @Test void lookupOccursAfterLocalLockWaitAndBeforeVerifiedEffect() {
        var grant=grant(); var time=new AtomicReference<>(GRANTED_AT.plusSeconds(1));
        var local=mock(PrescriptionClearancePort.class); var remote=mock(PrescriptionClearanceAuthorityPort.class);
        doAnswer(call->{time.set(GRANTED_AT.plusSeconds(3));return null;}).when(local).lockTarget(grant.prescriptionId());
        when(local.findByTargetForUpdate(grant.prescriptionId())).thenReturn(List.of(grant));
        when(remote.lookup(grant.clearanceId(),"trace")).thenAnswer(call->new Observation(true,grant.clearanceId(),true,
                grant.invoiceId(),grant.accountId(),grant.patientId(),"PRESCRIPTION","OUTPATIENT_VISIT",grant.episode().id(),
                null,null,grant.grantedAt(),grant.expiresAt(),time.get()));
        var clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId z){return this;} public Instant instant(){return time.get();}};
        var service=new PrescriptionClearanceAuthorizationService(local,clock,Optional.of(remote),true);
        assertThat(service.requireValidAfterLocks(prescription(grant),"trace")).isEqualTo(grant);
        var order=inOrder(local,remote); order.verify(local).lockTarget(grant.prescriptionId());
        order.verify(local).findByTargetForUpdate(grant.prescriptionId());order.verify(remote).lookup(grant.clearanceId(),"trace");
        order.verify(local).markVerified(grant.clearanceId());
    }
    @Test void requiredAuthorityMissingOrUnavailableIsNotLocalPermission() {
        var grant=grant();var local=mock(PrescriptionClearancePort.class);
        when(local.findByTargetForUpdate(grant.prescriptionId())).thenReturn(List.of(grant));
        var service=new PrescriptionClearanceAuthorizationService(local,Clock.fixed(GRANTED_AT.plusSeconds(1),ZoneOffset.UTC),Optional.empty(),true);
        assertThatThrownBy(()->service.requireValidAfterLocks(prescription(grant),"trace")).isInstanceOf(PharmacyUpstreamUnavailableException.class);
        verify(local,never()).markVerified(any());
    }
}
