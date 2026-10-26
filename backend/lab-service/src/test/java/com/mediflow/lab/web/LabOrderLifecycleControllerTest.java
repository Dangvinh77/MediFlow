package com.mediflow.lab.web;

import com.mediflow.lab.application.dto.response.LabOrderLifecycleDTO;
import com.mediflow.lab.application.port.in.LookupLabOrderLifecycleUseCase;
import com.mediflow.lab.infrastructure.config.SecurityConfig;
import com.mediflow.lab.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.lab.infrastructure.security.LabServicePrincipal;
import com.mediflow.lab.infrastructure.web.CorrelationIdFilter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = LabOrderLifecycleController.class, properties = "mediflow.jwt.secret=lifecycle-test-secret-longer-than-32-bytes")
@Import({SecurityConfig.class, ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
class LabOrderLifecycleControllerTest {
    private static final UUID SOURCE = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String PATH = "/api/v2/lab/source-orders/" + SOURCE + "/lifecycle";
    @Autowired MockMvc mvc;
    @MockBean LookupLabOrderLifecycleUseCase lifecycle;

    @Test void verifiedServiceGetsExplicitMissingResponseWithoutInventedBindings() throws Exception {
        when(lifecycle.lookup(SOURCE)).thenReturn(new LabOrderLifecycleDTO(SOURCE, false,
                null, null, null, null, null, null, null, Instant.parse("2026-10-26T00:00:00Z")));
        mvc.perform(get(PATH).with(authentication(service())).header("X-Correlation-Id", "lab-lifecycle-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.exists").value(false))
                .andExpect(jsonPath("$.data.sourceOrderId").value(SOURCE.toString()))
                .andExpect(jsonPath("$.correlationId").value("lab-lifecycle-1"));
        verify(lifecycle).lookup(SOURCE);
    }

    @Test @WithMockUser(roles = "SYSTEM") void humanSystemRoleCannotImpersonateService() throws Exception {
        mvc.perform(get(PATH).header("X-Correlation-Id", "test")).andExpect(status().isForbidden());
        verifyNoInteractions(lifecycle);
    }

    @Test @WithMockUser(roles = "DOCTOR") void doctorCannotReadInternalEndpoint() throws Exception {
        mvc.perform(get(PATH).header("X-Correlation-Id", "test")).andExpect(status().isForbidden());
        verifyNoInteractions(lifecycle);
    }

    @Test void missingCorrelationAndUnexpectedQueryAreRejectedBeforeLookup() throws Exception {
        mvc.perform(get(PATH).with(authentication(service()))).andExpect(status().isBadRequest());
        mvc.perform(get(PATH).with(authentication(service())).header("X-Correlation-Id", "test")
                .param("patientId", UUID.randomUUID().toString())).andExpect(status().isBadRequest());
        verifyNoInteractions(lifecycle);
    }

    private static UsernamePasswordAuthenticationToken service() {
        return new UsernamePasswordAuthenticationToken(new LabServicePrincipal("clinical-service"), null,
                List.of(new SimpleGrantedAuthority("ROLE_SYSTEM")));
    }
}
