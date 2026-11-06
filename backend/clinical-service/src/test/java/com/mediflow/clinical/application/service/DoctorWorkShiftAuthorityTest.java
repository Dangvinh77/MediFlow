package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.mediflow.clinical.application.dto.authority.*;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.request.DoctorWorkShiftCreateRequest;
import com.mediflow.clinical.application.dto.request.ConsultationSessionCreateRequest;
import com.mediflow.clinical.domain.model.DoctorWorkShift;
import com.mediflow.clinical.application.port.out.*;
import com.mediflow.common.api.*;
import com.mediflow.common.exception.*;

class DoctorWorkShiftAuthorityTest {
    final UUID account=UUID.randomUUID(),staff=UUID.randomUUID(),department=UUID.randomUUID(),room=UUID.randomUUID(),correlation=UUID.randomUUID();
    final LocalDate day=LocalDate.of(2026,11,2);
    final Instant now=Instant.parse("2026-11-01T00:00:00Z");
    final ConsultationSessionRepositoryPort sessions=mock(ConsultationSessionRepositoryPort.class);
    final DoctorWorkShiftRepositoryPort shifts=mock(DoctorWorkShiftRepositoryPort.class);
    final SchedulingAuthorityPort authority=mock(SchedulingAuthorityPort.class);
    final CurrentClinicalActorPort actors=mock(CurrentClinicalActorPort.class);
    final CorrelationIdProvider correlations=mock(CorrelationIdProvider.class);
    final IdempotentCommandPort commands=mock(IdempotentCommandPort.class);
    final ConsultationSessionApplicationService schedules=new ConsultationSessionApplicationService(sessions,authority,actors,correlations,commands,Clock.fixed(now,ZoneOffset.UTC));
    final DoctorWorkShiftApplicationService service=new DoctorWorkShiftApplicationService(shifts,sessions,schedules,commands,Clock.fixed(now,ZoneOffset.UTC));
    @BeforeEach void setup(){when(correlations.currentOrCreate()).thenReturn(correlation);}
    @ParameterizedTest @ValueSource(strings={"ADMIN","DOCTOR"})
    void managementUsesActualSignedRoleAndCurrentCoreScope(String role) {
        when(actors.current()).thenReturn(new ActorIdentity(account,staff,null,role));
        grant(role,true,staff,department);
        when(shifts.search(department,day,day,PageQuery.of(0,20))).thenReturn(PageResult.of(List.of(),0,0,20));
        assertThat(service.search(department,day,day,PageQuery.of(0,20)).totalElements()).isZero();
        grant(role,false,staff,department);
        assertThatThrownBy(()->service.search(department,day,day,PageQuery.of(0,20))).isInstanceOf(ForbiddenOperationException.class);
        verify(authority,times(2)).authority(account,department,correlation.toString());
    }
    @Test void ordinaryRevokedMismatchedAndCrossDepartmentDoctorCannotCreate() {
        when(actors.current()).thenReturn(new ActorIdentity(account,staff,null,"DOCTOR"));
        var body=new DoctorWorkShiftCreateRequest(staff,department,room,day,LocalTime.of(8,0),LocalTime.of(11,0));
        grant("DOCTOR",false,staff,department);
        assertThatThrownBy(()->service.create(body,"key")).isInstanceOf(ForbiddenOperationException.class);
        grant("NURSE",true,staff,department);
        assertThatThrownBy(()->service.create(body,"key")).isInstanceOf(ForbiddenOperationException.class);
        grant("DOCTOR",true,UUID.randomUUID(),department);
        assertThatThrownBy(()->service.create(body,"key")).isInstanceOf(ForbiddenOperationException.class);
        grant("DOCTOR",true,staff,UUID.randomUUID());
        assertThatThrownBy(()->service.create(body,"key")).isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(commands,shifts);
    }
    @Test void coreFailureNeverReadsOrWritesShiftData() {
        when(actors.current()).thenReturn(new ActorIdentity(account,staff,null,"DOCTOR"));
        when(authority.authority(account,department,correlation.toString())).thenThrow(new IllegalStateException("Core unavailable"));
        assertThatThrownBy(()->service.search(department,day,day,PageQuery.of(0,20))).hasMessage("Core unavailable");
        verifyNoInteractions(shifts,commands);
    }
    @Test void mineUsesCurrentDoctorIdentityAndRequiresNoManagementGrant() {
        when(actors.current()).thenReturn(new ActorIdentity(account,staff,null,"DOCTOR"));
        when(authority.currentDoctorAuthority(account,correlation.toString())).thenReturn(new CurrentDoctorAuthoritySnapshot(account,true,true,"DOCTOR",staff,true,department,true,now));
        when(shifts.findByDoctor(staff,day,day)).thenReturn(List.of());
        assertThat(service.mine(day,day)).isEmpty();
        verify(authority,never()).authority(any(),any(),any()); verify(shifts).findByDoctor(staff,day,day);
    }
    @Test void invalidAndOverlongRangesCannotReadData() {
        assertThatThrownBy(()->service.mine(day,day.plusDays(31))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(()->service.mine(day.plusDays(1),day)).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(shifts);
    }
    @Test void summaryRequiresLiveHeadScopeAndProvidesFullScopeObservation() {
        when(actors.current()).thenReturn(new ActorIdentity(account,staff,null,"DOCTOR")); grant("DOCTOR",true,staff,department);
        when(sessions.summarize(department,day,day)).thenReturn(new ConsultationSessionRepositoryPort.SummaryCounts(25,2,1,100,2,3,4,5,86));
        var result=schedules.summary(department,day,day);
        assertThat(result.openSessionCount()).isEqualTo(25); assertThat(result.capacity()).isEqualTo(100); assertThat(result.observedAt()).isEqualTo(now);
        grant("DOCTOR",false,staff,department);
        assertThatThrownBy(()->schedules.summary(department,day,day)).isInstanceOf(ForbiddenOperationException.class);
        verify(sessions).summarize(department,day,day);
    }
    @Test void explicitSessionCreationLocksSourceThenInsertsAndLinksExactSession() {
        when(actors.current()).thenReturn(new ActorIdentity(account,staff,null,"DOCTOR")); grant("DOCTOR",true,staff,department);
        var shift=DoctorWorkShift.active(UUID.randomUUID(),staff,department,room,day,LocalTime.of(8,0),LocalTime.of(11,0));
        when(shifts.findById(shift.shiftId())).thenReturn(Optional.of(shift));
        when(shifts.findByIdForUpdate(shift.shiftId())).thenReturn(Optional.of(shift));
        when(authority.consultationEligibility(staff,department,room,correlation.toString())).thenReturn(new ConsultationEligibilitySnapshot(staff,true,true,true,department,department,true,true,room,true,true,department,"CONSULTATION",true,now));
        when(sessions.insert(any(),eq(account),isNull(),eq(now))).thenAnswer(invocation->invocation.getArgument(0));
        when(commands.execute(any(),any(),any(),any(),any(),any(),anyInt(),any(),any())).thenAnswer(invocation->{
            Object data=((java.util.function.Supplier<?>)invocation.getArgument(8)).get();
            return new IdempotentCommandPort.Result<>(data,201,null,false);
        });
        var request=new ConsultationSessionCreateRequest(staff,department,room,day,LocalTime.of(8,0),LocalTime.of(10,0),List.of(),8);
        var result=service.createSession(shift.shiftId(),request,"key");
        assertThat(result.data().capacity()).isEqualTo(8); assertThat(result.data().doctorId()).isEqualTo(staff);
        var order=inOrder(shifts,sessions); order.verify(shifts).findById(shift.shiftId());order.verify(shifts).findByIdForUpdate(shift.shiftId());
        order.verify(sessions).insert(any(),eq(account),isNull(),eq(now));order.verify(shifts).linkSession(shift.shiftId(),result.data().sessionId());
        when(shifts.findByIdForUpdate(shift.shiftId())).thenReturn(Optional.of(shift.cancel(1,"cancel")));
        assertThatThrownBy(()->service.createSession(shift.shiftId(),request,"new-key")).isInstanceOf(DuplicateResourceException.class).hasMessageContaining("SHIFT_INACTIVE");
        verify(sessions,times(1)).insert(any(),any(),any(),any());
    }
    void grant(String role,boolean allowed,UUID staffId,UUID departmentId) {
        when(authority.authority(account,department,correlation.toString())).thenReturn(new SchedulingAuthoritySnapshot(account,true,true,role,staffId,true,departmentId,true,allowed,1L,now));
    }
}
