package com.mediflow.billing.web;

import com.mediflow.billing.application.dto.response.InvoiceDTO;
import com.mediflow.billing.application.dto.response.PrescriptionAdjustmentDTO;
import com.mediflow.billing.application.port.in.GetPrescriptionAdjustmentUseCase;
import com.mediflow.billing.application.port.in.ManageInvoiceUseCase;
import com.mediflow.billing.domain.model.SagaStatus;
import com.mediflow.billing.infrastructure.config.SecurityConfig;
import com.mediflow.common.security.JwtClaims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises signed credentials through the real downstream security chain. */
@WebMvcTest(value = {InvoiceController.class, PrescriptionAdjustmentController.class}, properties = {
        "mediflow.billing.ledger.enabled=true", "mediflow.billing.prescription-charge-consumer.enabled=true",
        "mediflow.jwt.secret=test-only-human-token-boundary-secret-at-least-32-bytes"})
@Import(SecurityConfig.class)
class BillingHumanTokenBoundaryTest {
    private static final String SECRET = "test-only-human-token-boundary-secret-at-least-32-bytes";
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000019");
    @Autowired MockMvc mvc;
    @MockBean ManageInvoiceUseCase invoices;
    @MockBean GetPrescriptionAdjustmentUseCase adjustments;

    static Stream<Arguments> invalidCredentials() {
        return Stream.of("/api/v1/billing/invoices/" + ID,
                "/api/v1/billing/prescription-adjustments/" + ID + "/refunds-due")
                .flatMap(path -> Stream.of("refresh", "missing-type", "unknown-type", "missing-exp", "invalid-subject")
                        .map(scenario -> Arguments.of(path, scenario)));
    }

    @ParameterizedTest
    @MethodSource("invalidCredentials")
    void invalidHumanCredential_isRejectedBeforeBusinessRead(String path, String scenario) throws Exception {
        mvc.perform(get(path).header("Authorization", "Bearer " + token(scenario, "CASHIER")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        verifyNoInteractions(invoices, adjustments);
    }

    static Stream<Arguments> allowedCredentials() {
        return Stream.of("ADMIN", "CASHIER").flatMap(role -> Stream.of(
                "/api/v1/billing/invoices/" + ID,
                "/api/v1/billing/prescription-adjustments/" + ID + "/refunds-due")
                .map(path -> Arguments.of(path, role)));
    }

    @ParameterizedTest
    @MethodSource("allowedCredentials")
    void signedAccessCredential_reachesOnlyTheRequestedUseCase(String path, String role) throws Exception {
        when(invoices.getById(ID)).thenReturn(new InvoiceDTO(ID, ID, null, null, false, null, null,
                SagaStatus.NONE, null, List.of()));
        when(adjustments.get(ID)).thenReturn(new PrescriptionAdjustmentDTO(ID, "prescription.cancelled", "APPLIED", null, List.of()));
        mvc.perform(get(path).header("Authorization", "Bearer " + token("valid", role)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        if (path.contains("prescription-adjustments")) {
            verify(adjustments).get(ID);
            verifyNoInteractions(invoices);
        } else {
            verify(invoices).getById(ID);
            verifyNoInteractions(adjustments);
        }
    }

    private static String token(String scenario, String role) {
        var builder = Jwts.builder().subject("invalid-subject".equals(scenario) ? "cashier" : ID.toString())
                .claim(JwtClaims.ROLE, role);
        if (!"missing-type".equals(scenario)) builder.claim(JwtClaims.TYPE, switch (scenario) {
            case "refresh" -> "refresh";
            case "unknown-type" -> "unknown";
            default -> JwtClaims.ACCESS_TOKEN_TYPE;
        });
        if (!"missing-exp".equals(scenario)) builder.expiration(new Date(System.currentTimeMillis() + 300_000));
        return builder.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
