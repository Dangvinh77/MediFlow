package com.mediflow.billing.web;

import com.mediflow.billing.application.dto.response.PrescriptionAdjustmentDTO;
import com.mediflow.billing.application.port.in.GetPrescriptionAdjustmentUseCase;
import com.mediflow.common.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest(value = PrescriptionAdjustmentController.class, properties = {
        "mediflow.billing.ledger.enabled=true", "mediflow.billing.prescription-charge-consumer.enabled=true",
        "mediflow.jwt.secret=test-only-prescription-adjustment-jwt-secret-32-bytes"})
@org.springframework.context.annotation.Import(com.mediflow.billing.infrastructure.config.SecurityConfig.class)
class PrescriptionAdjustmentControllerTest {
    @Autowired MockMvc mvc;
    @MockBean GetPrescriptionAdjustmentUseCase adjustments;
    private final UUID rx = UUID.randomUUID();
    private String url() { return "/api/v1/billing/prescription-adjustments/" + rx + "/refunds-due"; }
    @ParameterizedTest @ValueSource(strings = {"ADMIN", "CASHIER"})
    void get_signedAllowedRole_receivesAggregateObligation(String role) throws Exception {
        when(adjustments.get(rx)).thenReturn(new PrescriptionAdjustmentDTO(rx, "prescription.cancelled", "APPLIED", null,
                List.of(new PrescriptionAdjustmentDTO.RefundDue(UUID.randomUUID(), new BigDecimal("750.00"), "VND"))));
        mvc.perform(get(url()).with(user("cashier").roles(role))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPLIED"))
                .andExpect(jsonPath("$.data.refundsDue[0].amount").value(750));
    }
    @ParameterizedTest @ValueSource(strings = {"MANAGER", "DOCTOR", "NURSE", "PHARMACIST", "LAB_TECH", "PATIENT", "SYSTEM"})
    void get_otherRole_deniedBeforeLedgerRead(String role) throws Exception {
        mvc.perform(get(url()).with(user("other").roles(role))).andExpect(status().isForbidden()); verifyNoInteractions(adjustments);
    }
    @Test void get_unauthenticated_deniedBeforeLedgerRead() throws Exception {
        mvc.perform(get(url())).andExpect(status().isUnauthorized()); verifyNoInteractions(adjustments);
    }
    @Test @WithMockUser(roles = "CASHIER") void get_missingAdjustment_notZeroRefund() throws Exception {
        when(adjustments.get(rx)).thenThrow(new ResourceNotFoundException("BILLING_PRESCRIPTION_ADJUSTMENT_NOT_FOUND", "not found"));
        mvc.perform(get(url())).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("BILLING_PRESCRIPTION_ADJUSTMENT_NOT_FOUND"));
    }
    @Test @WithMockUser(roles = "CASHIER") void get_storageFailure_unavailableNotZero() throws Exception {
        when(adjustments.get(rx)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private detail"));
        mvc.perform(get(url())).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("BILLING_LEDGER_UNAVAILABLE"));
    }
}
