package com.mediflow.billing.web;

import com.mediflow.billing.application.dto.response.ScheduledPaymentResult;
import com.mediflow.billing.application.service.ScheduledPaymentCoordinator;
import com.mediflow.billing.domain.model.PaymentOperationStatus;
import com.mediflow.billing.infrastructure.config.SecurityConfig;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScheduledZeroEligibilityController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"mediflow.billing.ledger.enabled=true",
        "mediflow.billing.scheduled-payments.enabled=true", "mediflow.billing.outpatient-quotes.enabled=true",
        "mediflow.billing.zero-payable-eligibility.enabled=true",
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes"})
class ScheduledZeroEligibilityControllerTest {
    private static final String ACTOR = "11111111-1111-4111-8111-111111111111";
    private static final UUID REQUEST = UUID.randomUUID(), RESERVATION = UUID.randomUUID(), QUOTE = UUID.randomUUID();
    @Autowired MockMvc http;
    @MockBean ScheduledPaymentCoordinator payments;

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String body) {
        return post("/api/v2/billing/payment-requests/" + REQUEST + "/zero-payable-eligibility")
                .header("Idempotency-Key", "zero-confirm-1").header("X-Correlation-Id", "zero-web-test")
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }
    private String body() {
        return "{\"reservationId\":\"" + RESERVATION + "\",\"expectedReservationRevision\":1,\"quoteId\":\"" + QUOTE + "\"}";
    }
    private ScheduledPaymentResult result(PaymentOperationStatus status) {
        Instant now = Instant.parse("2026-10-26T01:00:00Z");
        return new ScheduledPaymentResult(new ScheduledPaymentResult.OperationDTO(UUID.randomUUID(), REQUEST,
                UUID.randomUUID(), RESERVATION, status, null, "0.00", "VND", now, now,
                "ZERO_LIABILITY", QUOTE, status == PaymentOperationStatus.COMPLETED ? now : null),
                null, status == PaymentOperationStatus.COMPLETED ? UUID.randomUUID() : null);
    }
    @Test @WithMockUser(username = ACTOR, roles = "CASHIER")
    void zeroReceiptHasNoCashPaymentAndKeepsExactQuote() throws Exception {
        when(payments.pay(eq(REQUEST), any(), eq(UUID.fromString(ACTOR)), eq("zero-confirm-1"), eq("zero-web-test"), any()))
                .thenReturn(result(PaymentOperationStatus.COMPLETED));
        http.perform(request(body())).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.payment").doesNotExist())
                .andExpect(jsonPath("$.data.operation.basis").value("ZERO_LIABILITY"))
                .andExpect(jsonPath("$.data.operation.quoteId").value(QUOTE.toString()));
        verify(payments).pay(eq(REQUEST), argThat(c -> "ZERO_LIABILITY".equals(c.basis())
                && QUOTE.equals(c.quoteId()) && c.amount().signum() == 0), any(), any(), any(), any());
    }
    @Test @WithMockUser(username = ACTOR, roles = "ADMIN")
    void preparingReturns202ForTheSameOperation() throws Exception {
        when(payments.pay(any(), any(), any(), any(), any(), any())).thenReturn(result(PaymentOperationStatus.PREPARING));
        http.perform(request(body())).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.operation.status").value("PREPARING"));
    }
    @Test @WithMockUser(username = ACTOR, roles = "CASHIER")
    void closedReturnsFailureEnvelope() throws Exception {
        when(payments.pay(any(), any(), any(), any(), any(), any())).thenReturn(result(PaymentOperationStatus.CLOSED_NOT_PAID));
        http.perform(request(body())).andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false));
    }
    @Test @WithMockUser(username = ACTOR, roles = "PATIENT")
    void patientCannotGrantItsOwnEligibility() throws Exception {
        http.perform(request(body())).andExpect(status().isForbidden());
        verifyNoInteractions(payments);
    }
    @Test @WithMockUser(username = ACTOR, roles = "CASHIER")
    void missingQuoteIsRejectedBeforeCoordinating() throws Exception {
        http.perform(request("{\"reservationId\":\"" + RESERVATION + "\",\"expectedReservationRevision\":1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(payments);
    }
}
