package com.mediflow.billing.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.mediflow.billing.application.dto.response.RevenueByDeptDTO;
import com.mediflow.billing.application.port.in.QueryRevenueUseCase;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web-slice test cho {@link RevenueController} (BR-B10). */
@WebMvcTest(RevenueController.class)
@Import(RevenueControllerTest.MethodSecurityConfiguration.class)
class RevenueControllerTest {

    private static final String BASE_PATH = "/api/v1/billing/revenue";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QueryRevenueUseCase queryRevenueUseCase;

    @Test
    @WithMockUser(roles = "MANAGER")
    void revenueByDepartment_returnsGroupedRevenue() throws Exception {
        UUID departmentId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 31);
        when(queryRevenueUseCase.revenueByDepartment(departmentId, from, to))
                .thenReturn(List.of(new RevenueByDeptDTO(departmentId, BigDecimal.valueOf(1_000_000), 3)));

        mockMvc.perform(get(BASE_PATH)
                        .param("departmentId", departmentId.toString())
                        .param("fromDate", from.toString())
                        .param("toDate", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].invoiceCount").value(3));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void revenueByDepartment_departmentIdOmitted_meansAllDepartments() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 31);
        when(queryRevenueUseCase.revenueByDepartment(null, from, to)).thenReturn(List.of());

        mockMvc.perform(get(BASE_PATH)
                        .param("fromDate", from.toString())
                        .param("toDate", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void revenueByDepartment_forbiddenRole_returns403() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .param("fromDate", "2026-08-01")
                        .param("toDate", "2026-08-31"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(queryRevenueUseCase);
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }
}
