package com.mediflow.billing.web;

import com.mediflow.billing.application.port.in.FindPaymentRequestQueueUseCase;
import com.mediflow.billing.infrastructure.config.SecurityConfig;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.billing.application.dto.command.PaymentRequestSourcePurpose;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentRequestQueueController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"mediflow.billing.ledger.enabled=true", "mediflow.billing.payment-request-discovery.enabled=true", "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes"})
class PaymentRequestQueueControllerTest {
    private static final String PATH="/api/v1/billing/payment-requests/queue";
    private static final String SECRET="test-secret-must-have-at-least-32-bytes";
    @Autowired MockMvc http;
    @MockBean FindPaymentRequestQueueUseCase queue;

    private String token(String role,String type,Instant expiration,String secret) {
        var builder=Jwts.builder().subject(UUID.randomUUID().toString()).claim("role",role).claim("type",type);
        if (expiration != null) builder.expiration(Date.from(expiration));
        return builder.signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }
    private String access(String role) { return token(role,"access",Instant.now().plusSeconds(60),SECRET); }

    @ParameterizedTest @ValueSource(strings={"ADMIN","CASHIER"})
    void queueReturnsPageAndNoStoreWithSignedHumanCredential(String role) throws Exception {
        when(queue.find(null,null,null,PageQuery.of(null,null))).thenReturn(PageResult.empty(PageQuery.of(null,null)));
        http.perform(get(PATH).header("Authorization","Bearer "+access(role)).header("X-Correlation-Id","queue-trace"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().string("X-Correlation-Id","queue-trace"))
            .andExpect(jsonPath("$.correlationId").value("queue-trace"))
            .andExpect(jsonPath("$.data.size").value(20)).andExpect(jsonPath("$.data.content").isArray());
    }
    @Test void allOptionalFiltersBindAndPageBoundsUseCommonConvention() throws Exception {
        UUID patient=UUID.randomUUID(),episode=UUID.randomUUID();
        when(queue.find(patient,episode,PaymentRequestSourcePurpose.PRESCRIPTION,PageQuery.of(-1,500)))
            .thenReturn(PageResult.empty(PageQuery.of(-1,500)));
        http.perform(get(PATH).header("Authorization","Bearer "+access("CASHIER"))
            .param("patientId",patient.toString()).param("careEpisodeId",episode.toString())
            .param("purpose","PRESCRIPTION").param("page","-1").param("size","500"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.number").value(0))
            .andExpect(jsonPath("$.data.size").value(100)).andExpect(jsonPath("$.correlationId").isNotEmpty());
        verify(queue).find(patient,episode,PaymentRequestSourcePurpose.PRESCRIPTION,PageQuery.of(-1,500));
    }
    @ParameterizedTest @ValueSource(strings={"PATIENT","DOCTOR","NURSE","LAB_TECH","PHARMACIST"})
    void otherHumanRolesCannotBrowse(String role) throws Exception {
        http.perform(get(PATH).header("Authorization","Bearer "+access(role))).andExpect(status().isForbidden());
        verifyNoInteractions(queue);
    }
    @ParameterizedTest @ValueSource(strings={"service","refresh"})
    void nonAccessTypesNeverReachUseCase(String type) throws Exception {
        http.perform(get(PATH).header("Authorization","Bearer "+token("ADMIN",type,Instant.now().plusSeconds(60),SECRET)))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(queue);
    }
    @Test void missingMalformedExpiredUnsignedAndSystemCredentialsAreRejected() throws Exception {
        http.perform(get(PATH)).andExpect(status().isUnauthorized());
        for(String token : java.util.List.of("bad-token",access("SYSTEM"),
            token("ADMIN","access",null,SECRET),token("ADMIN","access",Instant.now().minusSeconds(60),SECRET),
            token("ADMIN","access",Instant.now().plusSeconds(60),"a-different-secret-at-least-32-bytes"))) {
            http.perform(get(PATH).header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(queue);
    }
    @ParameterizedTest @ValueSource(strings={"patientId","careEpisodeId","purpose","page","size","actorId"})
    void malformedOrUnknownFilterFailsBeforeUseCase(String key) throws Exception {
        http.perform(get(PATH).header("Authorization","Bearer "+access("ADMIN")).param(key,"bad"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
            .andExpect(header().string("Cache-Control","no-store"));
        verifyNoInteractions(queue);
    }
    @ParameterizedTest @ValueSource(strings={"EXAM","SURGERY","ADMISSION",""})
    void unsupportedPurposeIsRejected(String purpose) throws Exception {
        http.perform(get(PATH).header("Authorization","Bearer "+access("CASHIER")).param("purpose",purpose))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(queue);
    }
    @Test void repeatedFiltersAreRejected() throws Exception {
        http.perform(get(PATH).header("Authorization","Bearer "+access("CASHIER")).param("purpose","LAB_TEST","PRESCRIPTION"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(queue);
    }
    @Test void corruptMappingIsUnavailableAndCarriesCorrelationWithoutPrivateDetails() throws Exception {
        when(queue.find(null,null,null,PageQuery.of(null,null)))
            .thenThrow(new org.springframework.dao.DataIntegrityViolationException("private mapping detail"));
        http.perform(get(PATH).header("Authorization","Bearer "+access("CASHIER")).header("X-Correlation-Id","queue-failure"))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("BILLING_LEDGER_UNAVAILABLE"))
            .andExpect(header().string("X-Correlation-Id","queue-failure"))
            .andExpect(jsonPath("$.correlationId").value("queue-failure"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private mapping detail"))));
    }
}
