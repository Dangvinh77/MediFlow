package com.mediflow.pharmacy.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mediflow.pharmacy.infrastructure.config.SecurityConfig;
import com.mediflow.pharmacy.infrastructure.messaging.PharmacyOutboxMaintenance;
import com.mediflow.pharmacy.infrastructure.security.JwtAuthFilter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Verifies that outbox replay remains an authenticated administrator-only operation. */
@WebMvcTest(PharmacyOutboxAdminController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes",
        "mediflow.pharmacy.outbox.enabled=true",
        "mediflow.pharmacy.outbox.maintenance-enabled=true"
})
class PharmacyOutboxAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PharmacyOutboxMaintenance maintenance;

    /** An administrator can requeue a quarantined event with its original identity. */
    @Test
    void replay_admin_requeuesEvent() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(maintenance.replay(eventId)).thenReturn(true);

        mockMvc.perform(post("/api/v1/pharmacy/admin/outbox/{eventId}/replay", eventId)
                        .with(user(UUID.randomUUID().toString()).roles("ADMIN"))
                        .header("X-Correlation-ID", "replay-correlation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.data.replayed").value(true))
                .andExpect(jsonPath("$.correlationId").value("replay-correlation"));
    }

    /** A non-administrator cannot invoke operational replay. */
    @Test
    void replay_pharmacist_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/pharmacy/admin/outbox/{eventId}/replay", UUID.randomUUID())
                        .with(user(UUID.randomUUID().toString()).roles("PHARMACIST")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(maintenance);
    }

    /** Missing events use the shared error envelope instead of returning an empty response. */
    @Test
    void replay_missingEvent_returnsEnvelope404() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(maintenance.replay(eventId)).thenReturn(false);

        mockMvc.perform(post("/api/v1/pharmacy/admin/outbox/{eventId}/replay", eventId)
                        .with(user(UUID.randomUUID().toString()).roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("OUTBOX_EVENT_NOT_FOUND"));
    }
}
