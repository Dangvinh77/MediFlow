package com.mediflow.report.web;

import com.mediflow.report.application.dto.response.FinancialArchiveRestoreDTO;
import com.mediflow.report.application.port.in.ManageFinancialSourceArchivesUseCase;
import com.mediflow.report.infrastructure.config.SecurityConfig;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FinancialSourceArchiveController.class)
@Import({SecurityConfig.class,GlobalExceptionHandler.class})
@TestPropertySource(properties={"mediflow.features.care-finance-v2=true", "mediflow.report.financial-archive.enabled=true",
        "mediflow.jwt.secret=archive-test-secret-at-least-32-bytes"})
class FinancialSourceArchiveControllerTest {
    private static final String PATH = "/api/v1/reports/financial/source-archives";
    private static final String SECRET = "archive-test-secret-at-least-32-bytes";
    @Autowired MockMvc mvc;
    @MockBean ManageFinancialSourceArchivesUseCase archives;

    @Test void export_admin_returnsDownloadWithoutCacheOrPayloadEnvelope() throws Exception {
        UUID id = UUID.randomUUID(); byte[] bytes = "opaque archive".getBytes(StandardCharsets.UTF_8);
        when(archives.exportArchive(id)).thenReturn(bytes);
        mvc.perform(get(PATH+"/"+id).with(user(UUID.randomUUID().toString()).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes)).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(header().string("X-Content-Type-Options","nosniff"))
                .andExpect(header().string("Content-Disposition","attachment; filename=financial-source-"+id+".json"));
    }
    @Test void restore_admin_usesAuthenticatedActorAndReturnsIsolatedGeneration() throws Exception {
        UUID actor = UUID.randomUUID(); UUID origin = UUID.randomUUID(); UUID restored = UUID.randomUUID();
        byte[] bytes = "archive evidence".getBytes(StandardCharsets.UTF_8);
        when(archives.restoreArchive(aryEq(bytes),eq(actor))).thenReturn(new FinancialArchiveRestoreDTO(restored,origin,"a".repeat(64)));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_OCTET_STREAM).content(bytes)
                .with(user(actor.toString()).roles("ADMIN")).header("X-Correlation-Id","archive-restore"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.generationId").value(restored.toString()))
                .andExpect(jsonPath("$.data.originGenerationId").value(origin.toString()))
                .andExpect(jsonPath("$.correlationId").value("archive-restore"))
                .andExpect(jsonPath("$.data.acceptanceReference").doesNotExist());
        verify(archives).restoreArchive(aryEq(bytes),eq(actor));
    }
    @ParameterizedTest @ValueSource(strings={"MANAGER","DOCTOR","NURSE","PHARMACIST","CASHIER","LAB_TECH","PATIENT","SYSTEM"})
    void archive_nonAdmin_cannotReadEvidenceOrRestore(String role) throws Exception {
        mvc.perform(get(PATH+"/"+UUID.randomUUID()).with(user(UUID.randomUUID().toString()).roles(role))).andExpect(status().isForbidden());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_OCTET_STREAM).content("private")
                .with(user(UUID.randomUUID().toString()).roles(role))).andExpect(status().isForbidden());
        verifyNoInteractions(archives);
    }
    @Test void archive_anonymous_deniedBeforeEvidence() throws Exception {
        mvc.perform(get(PATH+"/"+UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_OCTET_STREAM).content("private")).andExpect(status().isUnauthorized());
        verifyNoInteractions(archives);
    }
    @Test void restore_emptyBody_rejectsBeforeImporter() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_OCTET_STREAM).with(user(UUID.randomUUID().toString()).roles("ADMIN")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("REPORT_FINANCIAL_ARCHIVE_INVALID"));
        verifyNoInteractions(archives);
    }
    @Test void export_unknownGeneration_returns404NotAnEmptySuccessfulArchive() throws Exception {
        when(archives.exportArchive(any())).thenThrow(new com.mediflow.report.application.exception.ReportProjectionUnavailableException());
        mvc.perform(get(PATH+"/"+UUID.randomUUID()).with(user(UUID.randomUUID().toString()).roles("ADMIN")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("REPORT_NOT_FOUND"));
    }
    @Test void restore_oversizedDeclaredBody_rejectsBeforeReadingStream() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_OCTET_STREAM).content("x")
                .with(user(UUID.randomUUID().toString()).roles("ADMIN"))
                .with(request -> { var declared = spy(request); doReturn(33554433L).when(declared).getContentLengthLong(); return declared; }))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(archives);
    }
    @ParameterizedTest @ValueSource(strings={"refresh","service","missing-type","missing-expiry","non-uuid"})
    void archive_invalidSignedIdentity_returns401(String kind) throws Exception {
        var token = Jwts.builder().subject(kind.equals("non-uuid")?"wrong":UUID.randomUUID().toString()).claim("role","ADMIN");
        if (!kind.equals("missing-type")) token.claim("type",kind.equals("missing-expiry")||kind.equals("non-uuid")?"access":kind);
        if (!kind.equals("missing-expiry")) token.expiration(Date.from(Instant.now().plusSeconds(120)));
        String signed = token.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        mvc.perform(get(PATH+"/"+UUID.randomUUID()).header("Authorization","Bearer "+signed)).andExpect(status().isUnauthorized());
        verifyNoInteractions(archives);
    }
}
