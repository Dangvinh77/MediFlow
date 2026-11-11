package com.mediflow.lab.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.lab.application.port.out.LabCatalogPort;
import com.mediflow.lab.application.port.out.LabReportRepositoryPort;

/** With review on, the v1 result entry may not bypass the second-person release. */
class LabReviewGateTest {
    final LabReportRepositoryPort reports = mock(LabReportRepositoryPort.class);
    final LabCatalogPort catalog = mock(LabCatalogPort.class);
    final UUID testId = UUID.randomUUID();

    @Test void catalogueBoundTestNeedsReviewBeforeAnyDraftExists() {
        when(reports.workflowVersion(testId)).thenReturn(0);
        when(catalog.binding(testId)).thenReturn(Optional.of(new LabCatalogPort.TemplateBinding(testId, "GLU", "GLU", 1)));
        assertThat(LabApplicationConfig.reviewGate(true, reports, catalog).reviewRequired(testId)).isTrue();
    }

    @Test void testEnteredIntoTheWorkflowNeedsReview() {
        when(reports.workflowVersion(testId)).thenReturn(1);
        when(catalog.binding(testId)).thenReturn(Optional.empty());
        assertThat(LabApplicationConfig.reviewGate(true, reports, catalog).reviewRequired(testId)).isTrue();
    }

    @Test void legacyTestWithoutTemplateKeepsTheV1Entry() {
        when(reports.workflowVersion(testId)).thenReturn(0);
        when(catalog.binding(testId)).thenReturn(Optional.empty());
        assertThat(LabApplicationConfig.reviewGate(true, reports, catalog).reviewRequired(testId)).isFalse();
    }

    @Test void reviewOffNeverBlocks() {
        when(catalog.binding(testId)).thenReturn(Optional.of(new LabCatalogPort.TemplateBinding(testId, "GLU", "GLU", 1)));
        assertThat(LabApplicationConfig.reviewGate(false, reports, catalog).reviewRequired(testId)).isFalse();
    }
}
