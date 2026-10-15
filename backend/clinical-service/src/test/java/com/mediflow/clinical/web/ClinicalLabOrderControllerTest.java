package com.mediflow.clinical.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mediflow.clinical.application.dto.response.ClinicalLabOrderDTO;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.port.in.CreateClinicalLabOrderUseCase;
import com.mediflow.clinical.domain.model.CareEpisodeType;
import com.mediflow.clinical.infrastructure.config.SecurityConfig;
import com.mediflow.clinical.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.clinical.infrastructure.security.CurrentClinicalActorAdapter;
import com.mediflow.clinical.infrastructure.web.CorrelationIdFilter;
import com.mediflow.common.exception.ForbiddenOperationException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ClinicalLabOrderController.class, properties =
        "mediflow.jwt.secret=clinical-write-test-secret-at-least-32-bytes")
@Import({SecurityConfig.class, ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
class ClinicalLabOrderControllerTest {
    private static final String SECRET = "clinical-write-test-secret-at-least-32-bytes";
    private static final UUID RECORD = UUID.fromString("00000000-0000-0000-0000-000000001002");
    private static final UUID DOCTOR = UUID.fromString("00000000-0000-0000-0000-000000001004");
    private static final UUID ORDER = UUID.fromString("00000000-0000-0000-0000-000000001001");
    @Autowired MockMvc mvc;
    @MockBean CreateClinicalLabOrderUseCase orders;

    @Test
    void post_doctorCanCreateAndReceivesGeneratedOrderIdentity() throws Exception {
        when(orders.create(eq(RECORD), org.mockito.ArgumentMatchers.any())).thenReturn(
                new ClinicalLabOrderDTO(ORDER, RECORD, UUID.randomUUID(), DOCTOR, UUID.randomUUID(),
                        CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), "CBC", LocalDate.of(2026, 9, 21),
                        "LAB-CBC", Instant.parse("2026-09-21T10:00:00Z")));

        mvc.perform(post("/api/v1/records/" + RECORD + "/lab-orders")
                        .header("Authorization", "Bearer " + accessToken("DOCTOR", DOCTOR))
                        .header("X-Correlation-Id", "00000000-0000-0000-0000-000000001099")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"labType\":\"CBC\",\"requestedDate\":\"2026-09-21\",\"priceCode\":\"LAB-CBC\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sourceOrderId").value(ORDER.toString()))
                .andExpect(jsonPath("$.data.priceCode").value("LAB-CBC"))
                .andExpect(jsonPath("$.correlationId").value("00000000-0000-0000-0000-000000001099"));
        verify(orders).create(eq(RECORD), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void post_rejectsOtherRolesAndMalformedPriceCodeBeforeCallingUseCase() throws Exception {
        mvc.perform(post("/api/v1/records/" + RECORD + "/lab-orders")
                        .header("Authorization", "Bearer " + accessToken("LAB_TECH", DOCTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"labType\":\"CBC\",\"requestedDate\":\"2026-09-21\",\"priceCode\":\"LAB-CBC\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/records/" + RECORD + "/lab-orders")
                        .header("Authorization", "Bearer " + accessToken("DOCTOR", DOCTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"labType\":\"CBC\",\"requestedDate\":\"2026-09-21\",\"priceCode\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(orders);
    }

    @Test
    void post_passesOnlyTheSignedDoctorIdentityToClinicalAuthority() throws Exception {
        when(orders.create(eq(RECORD), org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            ActorIdentity actor = new CurrentClinicalActorAdapter().current();
            assertThat(actor).isNotNull();
            if (!new ActorIdentity(DOCTOR, "DOCTOR").equals(actor)) {
                throw new ForbiddenOperationException("CLINICAL_DOCTOR_NOT_ASSIGNED",
                        "The signed doctor does not match the assigned doctor");
            }
            return new ClinicalLabOrderDTO(ORDER, RECORD, UUID.randomUUID(), DOCTOR, UUID.randomUUID(),
                    CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), "CBC", LocalDate.of(2026, 9, 21),
                    "LAB-CBC", Instant.parse("2026-09-21T10:00:00Z"));
        });

        mvc.perform(post("/api/v1/records/" + RECORD + "/lab-orders")
                        .header("Authorization", "Bearer " + accessToken("DOCTOR", DOCTOR))
                        .header("X-Correlation-Id", "00000000-0000-0000-0000-000000001099")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"labType\":\"CBC\",\"requestedDate\":\"2026-09-21\",\"priceCode\":\"LAB-CBC\"}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/records/" + RECORD + "/lab-orders")
                        .header("Authorization", "Bearer " + accessToken("DOCTOR", UUID.randomUUID()))
                        .header("X-Correlation-Id", "00000000-0000-0000-0000-000000001099")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"labType\":\"CBC\",\"requestedDate\":\"2026-09-21\",\"priceCode\":\"LAB-CBC\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CLINICAL_DOCTOR_NOT_ASSIGNED"));
        verify(orders, org.mockito.Mockito.times(2)).create(eq(RECORD), org.mockito.ArgumentMatchers.any());
    }

    private static String accessToken(String role, UUID staffId) {
        Instant issuedAt = Instant.now().minusSeconds(1);
        return Jwts.builder().subject("account-01").claim("role", role).claim("type", "access")
                .claim("staffId", staffId.toString()).issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
