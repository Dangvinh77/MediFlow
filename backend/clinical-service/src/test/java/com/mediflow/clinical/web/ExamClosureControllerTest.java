package com.mediflow.clinical.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.mediflow.clinical.application.dto.response.MedicalRecordDTO;
import com.mediflow.clinical.application.dto.response.RecordLabOrderDTO;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.service.ExamClosureApplicationService;
import com.mediflow.clinical.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.clinical.infrastructure.web.CorrelationIdFilter;
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

@WebMvcTest(value = ExamClosureController.class, properties = "mediflow.features.care-finance-v2.enabled=true")
@Import({ExamClosureControllerTest.MethodSecurity.class, ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
class ExamClosureControllerTest {
    private static final UUID RECORD = UUID.randomUUID();
    private static final String PATH = "/api/v2/records/" + RECORD + "/close-without-conclusion";
    private static final String BODY = "{\"reason\":\"PATIENT_LEFT\",\"note\":\"Tự ý về\"}";

    @TestConfiguration @EnableMethodSecurity static class MethodSecurity {}

    @Autowired MockMvc mvc;
    @MockBean ExamClosureApplicationService closure;

    @Test void anonymousIsUnauthorized() throws Exception {
        mvc.perform(put(PATH).with(csrf()).header("Idempotency-Key", "k").contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(closure);
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "NURSE", "CASHIER", "PATIENT"})
    void onlyDoctorsCanClose(String role) throws Exception {
        mvc.perform(put(PATH).with(csrf()).with(user("u").roles(role)).header("Idempotency-Key", "k")
                .contentType("application/json").content(BODY)).andExpect(status().isForbidden());
        verifyNoInteractions(closure);
    }

    @Test void idempotencyKeyIsRequired() throws Exception {
        mvc.perform(put(PATH).with(csrf()).with(user("d").roles("DOCTOR")).contentType("application/json").content(BODY))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(closure);
    }

    private static final UUID ORDER = UUID.randomUUID();
    private static final String WITHDRAW = "/api/v2/records/" + RECORD + "/lab-orders/" + ORDER + "/withdrawal";

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "NURSE", "CASHIER", "PATIENT"})
    void onlyDoctorsCanWithdraw(String role) throws Exception {
        mvc.perform(put(WITHDRAW).with(csrf()).with(user("u").roles(role)).header("Idempotency-Key", "k")
                .contentType("application/json").content("{\"reason\":\"Đổi ý\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(closure);
    }

    @Test void withdrawalNeedsIdempotencyKey() throws Exception {
        mvc.perform(put(WITHDRAW).with(csrf()).with(user("d").roles("DOCTOR")).contentType("application/json")
                .content("{\"reason\":\"Đổi ý\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(closure);
    }

    @Test void doctorWithdrawsUnreceivedOrder() throws Exception {
        when(closure.withdrawLabOrder(eq(RECORD), eq(ORDER), any(), eq("k"))).thenReturn(new IdempotentCommandPort.Result<>(
                new RecordLabOrderDTO(ORDER, null, "CBC", "WITHDRAWN", java.time.Instant.parse("2026-11-10T03:00:00Z")), 200, null, false));
        mvc.perform(put(WITHDRAW).with(csrf()).with(user("d").roles("DOCTOR")).header("Idempotency-Key", "k")
                .contentType("application/json").content("{\"reason\":\"Đổi ý\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.status").value("WITHDRAWN"));
    }

    @Test void doctorClosesAndReceivesTheRecord() throws Exception {
        when(closure.closeWithoutConclusion(eq(RECORD), any(), eq("k")))
                .thenReturn(new IdempotentCommandPort.Result<>(mock(MedicalRecordDTO.class), 200, null, false));
        mvc.perform(put(PATH).with(csrf()).with(user("d").roles("DOCTOR")).header("Idempotency-Key", "k")
                .contentType("application/json").content(BODY))
                .andExpect(status().isOk()).andExpect(header().string("Idempotency-Replayed", "false"));
    }
}
