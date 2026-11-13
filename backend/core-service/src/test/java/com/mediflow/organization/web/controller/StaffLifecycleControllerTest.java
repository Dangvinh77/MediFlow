package com.mediflow.organization.web.controller;

import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.organization.application.port.in.DeactivateStaffUseCase;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Staff;
import com.mediflow.organization.infrastructure.config.SecurityConfig;
import com.mediflow.organization.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.organization.infrastructure.web.CorrelationIdFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StaffLifecycleController.class)
@Import({SecurityConfig.class, ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
@TestPropertySource(properties = "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class StaffLifecycleControllerTest {
    @Autowired MockMvc mvc;
    @MockBean DeactivateStaffUseCase lifecycle;
    final UUID actor = UUID.randomUUID();

    @Test void adminUsesSignedActorAndReturnsExistingProfileContract() throws Exception {
        var member = Staff.create("A", UUID.randomUUID(), JobTitle.NURSE, null, null, null, null, "001234567890");
        member.deactivate();
        when(lifecycle.execute(member.getStaffId(), actor)).thenReturn(member);
        mvc.perform(put("/api/v1/org/staff/{id}/deactivate", member.getStaffId())
                .header("Authorization", "Bearer " + token("ADMIN", "access"))
                .header("X-Account-Id", UUID.randomUUID()).contentType("application/json").content("{}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.staffId").value(member.getStaffId().toString()))
                .andExpect(jsonPath("$.data.active").value(false));
        verify(lifecycle).execute(member.getStaffId(), actor);
    }

    @ParameterizedTest @ValueSource(strings = {"DOCTOR", "NURSE", "PHARMACIST", "CASHIER", "LAB_TECH", "PATIENT", "SYSTEM"})
    void deniesEveryOtherRole(String role) throws Exception {
        mvc.perform(put("/api/v1/org/staff/{id}/deactivate", UUID.randomUUID())
                .header("Authorization", "Bearer " + token(role, role.equals("SYSTEM") ? "service" : "access"))
                .contentType("application/json").content("{}"))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(lifecycle);
    }

    @Test void reportsAConflictWithoutReturningAnInactiveProfile() throws Exception {
        var id = UUID.randomUUID();
        when(lifecycle.execute(id, actor)).thenThrow(new DuplicateResourceException("STAFF_DEPARTMENT_HEAD", "Thay trưởng khoa trước"));
        mvc.perform(put("/api/v1/org/staff/{id}/deactivate", id).header("Authorization", "Bearer " + token("ADMIN", "access")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("STAFF_DEPARTMENT_HEAD"));
    }

    @Test void requiresAuthentication() throws Exception {
        mvc.perform(put("/api/v1/org/staff/{id}/deactivate", UUID.randomUUID())).andExpect(status().isUnauthorized());
        verifyNoInteractions(lifecycle);
    }

    private String token(String role, String type) {
        return Jwts.builder().subject(actor.toString()).claim("role", role).claim("type", type)
                .expiration(Date.from(Instant.now().plusSeconds(120)))
                .signWith(Keys.hmacShaKeyFor("test-secret-must-have-at-least-32-bytes".getBytes(StandardCharsets.UTF_8))).compact();
    }
}
