package com.mediflow.billing.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.mediflow.billing.application.port.in.ReplayBillingOutboxUseCase;

/** Security and response contract for operator-triggered outbox replay. */
@WebMvcTest(BillingOutboxAdminController.class)
@Import(BillingOutboxAdminControllerTest.MethodSecurityConfiguration.class)
@TestPropertySource(properties = {
        "mediflow.billing.outbox.enabled=true",
        "mediflow.billing.outbox.maintenance-enabled=true"
})
class BillingOutboxAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReplayBillingOutboxUseCase replayUseCase;

    @Test
    @WithMockUser(roles = "ADMIN")
    void replay_existingEvent_returns200() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(replayUseCase.replay(eventId)).thenReturn(true);

        mockMvc.perform(post("/api/v1/billing/admin/outbox/{eventId}/replay", eventId).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.data.replayed").value(true));
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void replay_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/billing/admin/outbox/{eventId}/replay", UUID.randomUUID())
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(replayUseCase);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void replay_missingEvent_returns404Envelope() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(replayUseCase.replay(eventId)).thenReturn(false);

        mockMvc.perform(post("/api/v1/billing/admin/outbox/{eventId}/replay", eventId).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("OUTBOX_EVENT_NOT_FOUND"));
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }
}
