package com.mediflow.report.application.service;

import com.mediflow.report.application.dto.response.FinancialRebuildProgress;
import com.mediflow.report.application.dto.response.FinancialRebuildProgress.Status;
import com.mediflow.report.application.port.out.FinancialRebuildStorePort;
import com.mediflow.report.domain.exception.ReportRuleException;
import com.mediflow.report.domain.model.FinancialSourceManifest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FinancialRebuildApplicationServiceTest {
    private final UUID id = UUID.randomUUID();
    private final LocalDate from = LocalDate.of(2026,10,9), to = from.plusDays(2);
    private FinancialRebuildStorePort store;
    private FinancialRebuildApplicationService service;

    @BeforeEach void setup() { store = mock(FinancialRebuildStorePort.class); service = new FinancialRebuildApplicationService(store); }
    @Test void invalidInputRejectsBeforeStorage() {
        for (int batch : new int[] {0,32}) assertThatThrownBy(() -> service.advance(id,batch)).isInstanceOf(ReportRuleException.class);
        assertThatThrownBy(() -> service.start(from,from.plusDays(366),"Asia/Bangkok")).isInstanceOf(ReportRuleException.class);
        assertThatThrownBy(() -> service.start(from,to,"not-a-zone")).isInstanceOf(ReportRuleException.class);
        assertThatThrownBy(() -> service.progress(null)).isInstanceOf(ReportRuleException.class);
        verifyNoInteractions(store);
    }
    @Test void unresolvedSourceFailsWithoutReadingOrWritingProjection() {
        when(store.lock(id)).thenReturn(progress(Status.BUILDING,1));
        service.advance(id,1);
        verify(store).fail(id,"REPORT_FINANCIAL_SOURCE_UNRESOLVED");
        verify(store,never()).manifest(any()); verify(store,never()).writeDays(any(),any(),any());
    }
    @Test void terminalRetryHasNoOtherEffects() {
        var terminal = progress(Status.VERIFIED,0); when(store.lock(id)).thenReturn(terminal);
        assertThat(service.advance(id,31)).isSameAs(terminal); verify(store).lock(id); verifyNoMoreInteractions(store);
    }
    @Test void corruptedManifestIsFailedBeforeAnyDayEffects() {
        when(store.lock(id)).thenReturn(progress(Status.BUILDING,0));
        when(store.manifest(any())).thenThrow(new ReportRuleException("REPORT_FINANCIAL_MANIFEST_INVALID","corrupt"));
        service.advance(id,31); verify(store).fail(id,"REPORT_FINANCIAL_MANIFEST_INVALID"); verify(store,never()).writeDays(any(),any(),any());
    }
    @Test void storageFailurePropagatesInsteadOfCommittingPartialEffectsAsFailed() {
        var current = progress(Status.BUILDING,0); when(store.lock(id)).thenReturn(current);
        when(store.manifest(current)).thenReturn(new FinancialSourceManifest(List.of(),List.of(),List.of(),List.of()));
        doThrow(new IllegalStateException("storage fault")).when(store).writeDays(any(),any(),any());
        assertThatThrownBy(() -> service.advance(id,1)).hasMessage("storage fault"); verify(store,never()).fail(any(),any());
    }
    private FinancialRebuildProgress progress(Status status,int unresolved) {
        return new FinancialRebuildProgress(id,status,from,to,"Asia/Bangkok",Instant.parse("2026-10-12T00:00:00Z"),
                0,unresolved,0,"a".repeat(64),null);
    }
}
