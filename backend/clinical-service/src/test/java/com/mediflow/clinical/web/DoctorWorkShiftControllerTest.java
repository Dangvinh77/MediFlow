package com.mediflow.clinical.web;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;
import com.mediflow.clinical.application.service.*;
import com.mediflow.clinical.application.dto.response.*;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.clinical.infrastructure.web.CorrelationIdFilter;
import com.mediflow.common.api.*;
import com.mediflow.common.exception.ForbiddenOperationException;

@WebMvcTest(value={DoctorWorkShiftController.class,ConsultationSessionController.class},properties="mediflow.clinical.scheduling.enabled=true")
@Import({DoctorWorkShiftControllerTest.MethodSecurity.class,ThreadLocalCorrelationIdProvider.class,CorrelationIdFilter.class})
class DoctorWorkShiftControllerTest {
    @Autowired MockMvc mvc;
    @MockBean DoctorWorkShiftApplicationService shifts;
    @MockBean ConsultationSessionApplicationService sessions;
    @MockBean OutpatientRegistrationApplicationService registrations;
    static final UUID DEPT=UUID.randomUUID();
    static final String PATH="/api/v2/doctor-work-shifts";
    static final String BODY="""
        {"doctorId":"00000000-0000-0000-0000-000000000001","departmentId":"00000000-0000-0000-0000-000000000002",
         "roomId":"00000000-0000-0000-0000-000000000003","shiftDate":"2026-11-02","sessionDate":"2026-11-02",
         "startTime":"08:00","endTime":"11:00","expectedRevision":1,"reason":"change","capacity":8,"breaks":[],"status":"STOPPED"}
        """;
    @ParameterizedTest @ValueSource(strings={"NURSE","LAB_TECH","PHARMACIST","CASHIER","PATIENT"})
    void otherRolesCannotReadDepartmentShifts(String role) throws Exception {
        mvc.perform(get(PATH).with(user("actor").roles(role)).param("departmentId",DEPT.toString()).param("fromDate","2026-11-02").param("toDate","2026-11-02"))
                .andExpect(status().isForbidden()); verifyNoInteractions(shifts);
    }
    @ParameterizedTest @ValueSource(strings={"ADMIN","DOCTOR"})
    void managementTransportRolesReachLiveAuthorityService(String role) throws Exception {
        when(shifts.search(any(),any(),any(),any())).thenReturn(PageResult.of(List.of(),0,0,20));
        mvc.perform(get(PATH).with(user("actor").roles(role)).param("departmentId",DEPT.toString()).param("fromDate","2026-11-02").param("toDate","2026-11-02"))
                .andExpect(status().isOk()); verify(shifts).search(eq(DEPT),any(),any(),any());
    }
    @Test void ordinaryDoctorDeniedByCurrentAuthority() throws Exception {
        when(shifts.search(any(),any(),any(),any())).thenThrow(new ForbiddenOperationException("SCHEDULING_SCOPE_DENIED","No live appointment"));
        mvc.perform(get(PATH).with(user("actor").roles("DOCTOR")).param("departmentId",DEPT.toString()).param("fromDate","2026-11-02").param("toDate","2026-11-02"))
                .andExpect(status().isForbidden());
    }
    @Test void missingAndUnknownQueriesAreRejectedBeforeService() throws Exception {
        mvc.perform(get(PATH).with(user("actor").roles("DOCTOR")).param("fromDate","2026-11-02").param("toDate","2026-11-02")).andExpect(status().isBadRequest());
        mvc.perform(get(PATH).with(user("actor").roles("DOCTOR")).param("departmentId",DEPT.toString()).param("fromDate","2026-11-02").param("toDate","2026-11-02").param("doctorId",DEPT.toString())).andExpect(status().isUnprocessableEntity());
        mvc.perform(get(PATH+"/mine").with(user("actor").roles("DOCTOR")).param("fromDate","2026-11-02")).andExpect(status().isBadRequest());
        mvc.perform(get(PATH+"/mine").with(user("actor").roles("DOCTOR")).param("fromDate","2026-11-02").param("toDate","2026-11-02").param("doctorId",DEPT.toString())).andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(shifts);
    }
    @Test void mineIsOnlyDoctorAndCannotSelectAnotherDoctor() throws Exception {
        mvc.perform(get(PATH+"/mine").with(user("actor").roles("ADMIN")).param("fromDate","2026-11-02").param("toDate","2026-11-02")).andExpect(status().isForbidden());
        when(shifts.mine(any(),any())).thenReturn(List.of());
        mvc.perform(get(PATH+"/mine").with(user("actor").roles("DOCTOR")).param("fromDate","2026-11-02").param("toDate","2026-11-02")).andExpect(status().isOk());
    }
    @Test void summaryIsManagementOnlyAndRejectsUnknownOrMissingParameters() throws Exception {
        String path="/api/v2/consultation-sessions/summary";
        mvc.perform(get(path).with(user("actor").roles("NURSE")).param("departmentId",DEPT.toString()).param("fromDate","2026-11-02").param("toDate","2026-11-02")).andExpect(status().isForbidden());
        mvc.perform(get(path).with(user("actor").roles("DOCTOR")).param("fromDate","2026-11-02").param("toDate","2026-11-02")).andExpect(status().isBadRequest());
        mvc.perform(get(path).with(user("actor").roles("DOCTOR")).param("departmentId",DEPT.toString()).param("fromDate","2026-11-02").param("toDate","2026-11-02").param("page","0")).andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(sessions);
        when(sessions.summary(any(),any(),any())).thenReturn(new ConsultationSessionSummary(DEPT,LocalDate.of(2026,11,2),LocalDate.of(2026,11,2),25,1,0,100,2,3,4,5,86,Instant.parse("2026-11-01T00:00:00Z")));
        mvc.perform(get(path).with(user("actor").roles("DOCTOR")).param("departmentId",DEPT.toString()).param("fromDate","2026-11-02").param("toDate","2026-11-02"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.openSessionCount").value(25));
    }
    @Test void nurseCannotMutateAnyShiftRoute() throws Exception {
        for(String suffix:List.of("","/"+DEPT,"/"+DEPT+"/cancel","/"+DEPT+"/consultation-sessions")) {
            var request=(suffix.isEmpty()||suffix.endsWith("consultation-sessions"))?post(PATH+suffix):put(PATH+suffix);
            mvc.perform(request.with(user("actor").roles("NURSE")).with(csrf()).contentType("application/json").content(BODY)
                    .header("Idempotency-Key","test").header("X-Correlation-Id",UUID.randomUUID().toString())).andExpect(status().isForbidden());
        }
        verifyNoInteractions(shifts);
    }
    @Test void mutationRequiresCorrelationAndIdempotencyHeaders() throws Exception {
        mvc.perform(post(PATH).with(user("actor").roles("DOCTOR")).with(csrf()).contentType("application/json").content(BODY)
                .header("Idempotency-Key","test")).andExpect(status().isBadRequest());
        mvc.perform(post(PATH).with(user("actor").roles("DOCTOR")).with(csrf()).contentType("application/json").content(BODY)
                .header("X-Correlation-Id",UUID.randomUUID().toString())).andExpect(status().isBadRequest());
        verifyNoInteractions(shifts);
    }
    @Test void doctorCanReachMutationsButStillRequiresLiveAuthority() throws Exception {
        var denied=new ForbiddenOperationException("SCHEDULING_SCOPE_DENIED","No live appointment");
        when(shifts.create(any(),any())).thenThrow(denied); when(shifts.update(any(),any(),any())).thenThrow(denied);
        when(shifts.cancel(any(),any(),any())).thenThrow(denied); when(shifts.createSession(any(),any(),any())).thenThrow(denied);
        for(String suffix:List.of("","/"+DEPT,"/"+DEPT+"/cancel","/"+DEPT+"/consultation-sessions")) {
            var request=(suffix.isEmpty()||suffix.endsWith("consultation-sessions"))?post(PATH+suffix):put(PATH+suffix);
            mvc.perform(request.with(user("actor").roles("DOCTOR")).with(csrf()).contentType("application/json").content(BODY)
                    .header("Idempotency-Key","test").header("X-Correlation-Id",UUID.randomUUID().toString())).andExpect(status().isForbidden());
        }
        verify(shifts).create(any(),any()); verify(shifts).update(any(),any(),any()); verify(shifts).cancel(any(),any(),any()); verify(shifts).createSession(any(),any(),any());
    }
    @Test void consultationMutationsAdmitDoctorToLiveAuthorityAndDenyNurse() throws Exception {
        var denied=new ForbiddenOperationException("SCHEDULING_SCOPE_DENIED","No live appointment");
        when(sessions.create(any(),any())).thenThrow(denied);when(sessions.update(any(),any(),any())).thenThrow(denied);when(sessions.changeStatus(any(),any(),any())).thenThrow(denied);
        String base="/api/v2/consultation-sessions";
        for(String role:List.of("DOCTOR","NURSE")) for(String suffix:List.of("","/"+DEPT,"/"+DEPT+"/status")) {
            var request=suffix.isEmpty()?post(base):put(base+suffix);
            mvc.perform(request.with(user("actor").roles(role)).with(csrf()).contentType("application/json").content(BODY)
                    .header("Idempotency-Key","test").header("X-Correlation-Id",UUID.randomUUID().toString())).andExpect(status().isForbidden());
        }
        verify(sessions).create(any(),any());verify(sessions).update(any(),any(),any());verify(sessions).changeStatus(any(),any(),any());
    }
    @TestConfiguration @EnableMethodSecurity static class MethodSecurity {}
}
