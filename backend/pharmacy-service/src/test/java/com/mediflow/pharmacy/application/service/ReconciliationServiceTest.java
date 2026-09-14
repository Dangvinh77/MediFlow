package com.mediflow.pharmacy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.pharmacy.application.dto.response.ReconciliationFinding;
import com.mediflow.pharmacy.application.dto.response.ReconciliationReport;
import com.mediflow.pharmacy.application.port.out.LifecycleReconciliationRepositoryPort;

/** Ensures reconciliation remains an explicit dry-run with no repair side effects. */
class ReconciliationServiceTest {

    /** Reports every finding and preserves the dry-run marker. */
    @Test
    void reconcileDryRun_reportsFindingsWithoutMutationPort() {
        LifecycleReconciliationRepositoryPort repository = mock(LifecycleReconciliationRepositoryPort.class);
        ReconciliationFinding finding = new ReconciliationFinding(
                UUID.randomUUID(), "ACTIVE_RESERVATION_TERMINAL", "terminal reservation");
        when(repository.findMismatches(10)).thenReturn(List.of(finding));

        ReconciliationReport report = new ReconciliationService(repository).reconcileDryRun(10);

        assertThat(report.dryRun()).isTrue();
        assertThat(report.findings()).containsExactly(finding);
        verify(repository).findMismatches(10);
        verifyNoMoreInteractions(repository);
    }

    /** A non-positive limit cannot silently turn reconciliation into an unbounded scan. */
    @Test
    void reconcileDryRun_rejectsNonPositiveLimit() {
        LifecycleReconciliationRepositoryPort repository = mock(LifecycleReconciliationRepositoryPort.class);

        assertThatThrownBy(() -> new ReconciliationService(repository).reconcileDryRun(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
