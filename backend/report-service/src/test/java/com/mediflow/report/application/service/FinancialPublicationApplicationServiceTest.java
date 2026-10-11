package com.mediflow.report.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mediflow.report.application.dto.request.PublishFinancialSnapshotRequest;
import com.mediflow.report.application.dto.request.RollbackFinancialSnapshotRequest;
import com.mediflow.report.application.dto.response.FinancialPublicationDTO;
import com.mediflow.report.application.port.out.FinancialPublicationStorePort;
import com.mediflow.report.domain.exception.ReportRuleException;
import com.mediflow.report.domain.model.FinancialPublicationPolicy.HistoryPolicy;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FinancialPublicationApplicationServiceTest {

    private final FinancialPublicationStorePort store = mock(FinancialPublicationStorePort.class);
    private final FinancialPublicationApplicationService service =
            new FinancialPublicationApplicationService(store);

    private static final UUID ACTOR = UUID.randomUUID();

    private static PublishFinancialSnapshotRequest publishRequest() {
        return new PublishFinancialSnapshotRequest(
                UUID.randomUUID(), UUID.randomUUID(), null, "a".repeat(64),
                "acceptance-2026-10-11", HistoryPolicy.AVAILABLE_EVIDENCE_ONLY);
    }

    private static RollbackFinancialSnapshotRequest rollbackRequest() {
        return new RollbackFinancialSnapshotRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "rollback-2026-10-11");
    }

    @Test
    void publish_validBoundedEvidence_delegatesToStore() {
        var request = publishRequest();
        var expected = new FinancialPublicationDTO(
                request.commandId(), request.generationId(), null,
                Instant.parse("2026-10-11T00:00:00Z"), "PUBLISH");
        when(store.publish(request, ACTOR)).thenReturn(expected);

        var actual = service.publish(request, ACTOR);

        assertThat(actual).isEqualTo(expected);
        verify(store).publish(request, ACTOR);
    }

    @Test
    void publish_nullRequest_rejectsBeforeStore() {
        assertThatThrownBy(() -> service.publish(null, ACTOR))
                .isInstanceOf(ReportRuleException.class)
                .hasMessageContaining("Explicit bounded publication command");

        verifyNoInteractions(store);
    }

    @Test
    void publish_missingManifestFingerprint_rejectsBeforeStore() {
        var valid = publishRequest();
        var request = new PublishFinancialSnapshotRequest(
                valid.commandId(), valid.generationId(), valid.expectedCurrentGenerationId(),
                null, valid.acceptanceReference(), valid.historyPolicy());

        assertThatThrownBy(() -> service.publish(request, ACTOR))
                .isInstanceOf(ReportRuleException.class)
                .hasMessageContaining("finite-evidence history policy");

        verifyNoInteractions(store);
    }

    @Test
    void publish_wrongHistoryPolicy_rejectsBeforeStore() {
        var valid = publishRequest();
        var request = new PublishFinancialSnapshotRequest(
                valid.commandId(), valid.generationId(), valid.expectedCurrentGenerationId(),
                valid.manifestFingerprint(), valid.acceptanceReference(), null);

        assertThatThrownBy(() -> service.publish(request, ACTOR))
                .isInstanceOf(ReportRuleException.class);

        verifyNoInteractions(store);
    }

    @Test
    void publish_missingActor_rejectsBeforeStore() {
        assertThatThrownBy(() -> service.publish(publishRequest(), null))
                .isInstanceOf(ReportRuleException.class)
                .hasMessageContaining("identified operator");

        verifyNoInteractions(store);
    }

    @Test
    void publish_unboundedAcceptanceReference_rejectsBeforeStore() {
        var valid = publishRequest();
        var request = new PublishFinancialSnapshotRequest(
                valid.commandId(), valid.generationId(), valid.expectedCurrentGenerationId(),
                valid.manifestFingerprint(), "bad reference with spaces", valid.historyPolicy());

        assertThatThrownBy(() -> service.publish(request, ACTOR))
                .isInstanceOf(ReportRuleException.class);

        verifyNoInteractions(store);
    }

    @Test
    void rollback_validExpectedGeneration_delegatesToStore() {
        var request = rollbackRequest();
        var expected = new FinancialPublicationDTO(
                request.commandId(), request.generationId(), request.expectedCurrentGenerationId(),
                Instant.parse("2026-10-11T00:00:00Z"), "ROLLBACK");
        when(store.rollback(request, ACTOR)).thenReturn(expected);

        var actual = service.rollback(request, ACTOR);

        assertThat(actual).isEqualTo(expected);
        verify(store).rollback(request, ACTOR);
    }

    @Test
    void rollback_missingExpectedGeneration_rejectsBeforeStore() {
        var valid = rollbackRequest();
        var request = new RollbackFinancialSnapshotRequest(
                valid.commandId(), valid.generationId(), null, valid.acceptanceReference());

        assertThatThrownBy(() -> service.rollback(request, ACTOR))
                .isInstanceOf(ReportRuleException.class)
                .hasMessageContaining("Explicit bounded publication command");

        verifyNoInteractions(store);
    }

    @Test
    void rollback_missingActor_rejectsBeforeStore() {
        assertThatThrownBy(() -> service.rollback(rollbackRequest(), null))
                .isInstanceOf(ReportRuleException.class);

        verifyNoInteractions(store);
    }
}
