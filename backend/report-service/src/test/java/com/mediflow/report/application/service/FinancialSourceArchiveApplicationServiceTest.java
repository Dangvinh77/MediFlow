package com.mediflow.report.application.service;

import com.mediflow.report.application.dto.response.FinancialArchiveRestoreDTO;
import com.mediflow.report.application.port.out.FinancialArchiveCodecPort;
import com.mediflow.report.application.port.out.FinancialSourceArchiveStorePort;
import com.mediflow.report.domain.exception.ReportRuleException;
import com.mediflow.report.domain.model.FinancialEvidenceArchive;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialSourceArchiveApplicationServiceTest {
    private final FinancialSourceArchiveStorePort store = mock(FinancialSourceArchiveStorePort.class);
    private final FinancialArchiveCodecPort codec = mock(FinancialArchiveCodecPort.class);
    private final FinancialSourceArchiveApplicationService service = new FinancialSourceArchiveApplicationService(store,codec);
    private FinancialEvidenceArchive empty() {
        return FinancialEvidenceArchive.create(UUID.randomUUID(),LocalDate.of(2026,10,9),LocalDate.of(2026,10,10),"Asia/Bangkok",
                Instant.parse("2026-10-11T00:00:00Z"),0,FinancialEvidenceArchive.manifestHash(List.of()),List.of());
    }
    @Test void export_readsFrozenEvidenceBeforeEncoding() {
        var archive = empty(); byte[] bytes = {1};
        when(store.exportArchive(archive.originGenerationId())).thenReturn(archive); when(codec.encode(archive)).thenReturn(bytes);
        assertThat(service.exportArchive(archive.originGenerationId())).isSameAs(bytes);
        var order = inOrder(store,codec); order.verify(store).exportArchive(archive.originGenerationId()); order.verify(codec).encode(archive);
        verifyNoMoreInteractions(store,codec);
    }
    @Test void restore_validatesBytesBeforeAnyStoreAndPreservesOperator() {
        var archive = empty(); UUID actor = UUID.randomUUID(); byte[] bytes = {1};
        var result = new FinancialArchiveRestoreDTO(UUID.randomUUID(),archive.originGenerationId(),archive.archiveFingerprint());
        when(codec.decode(bytes)).thenReturn(archive); when(store.restoreArchive(archive,actor)).thenReturn(result);
        assertThat(service.restoreArchive(bytes,actor)).isEqualTo(result);
        var order = inOrder(codec,store); order.verify(codec).decode(bytes); order.verify(store).restoreArchive(archive,actor);
        verifyNoMoreInteractions(codec,store);
    }
    @Test void restore_damagedBytes_neverWritesAnything() {
        byte[] bytes = {1}; when(codec.decode(bytes)).thenThrow(new ReportRuleException("REPORT_FINANCIAL_ARCHIVE_INVALID","damaged"));
        assertThatThrownBy(() -> service.restoreArchive(bytes,UUID.randomUUID())).isInstanceOf(ReportRuleException.class);
        verifyNoInteractions(store);
    }
    @Test void archive_missingIdentity_rejectsBeforePorts() {
        assertThatThrownBy(() -> service.exportArchive(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.restoreArchive(new byte[]{1},null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(store,codec);
    }
}
