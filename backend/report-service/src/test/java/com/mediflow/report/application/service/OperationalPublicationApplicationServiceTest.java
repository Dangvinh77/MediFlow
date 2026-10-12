package com.mediflow.report.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mediflow.report.application.dto.request.PublishOperationalSnapshotRequest;
import com.mediflow.report.application.dto.request.RollbackOperationalSnapshotRequest;
import com.mediflow.report.application.dto.response.OperationalPublicationDTO;
import com.mediflow.report.application.port.out.OperationalPublicationStorePort;
import com.mediflow.report.domain.exception.ReportRuleException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperationalPublicationApplicationServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T04:00:00Z"), ZoneOffset.UTC);
    private static final UUID ACTOR = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID COMMAND = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID GENERATION = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private static final UUID EXPECTED = UUID.fromString("00000000-0000-4000-8000-000000000004");

    private final OperationalPublicationStorePort store = mock(OperationalPublicationStorePort.class);
    private final OperationalPublicationApplicationService service =
            new OperationalPublicationApplicationService(store, CLOCK, ZONE);

    private static PublishOperationalSnapshotRequest publishRequest() {
        return new PublishOperationalSnapshotRequest(
                COMMAND, GENERATION, "a".repeat(64),
                LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 11),
                "approval/2026-10-11", EXPECTED);
    }

    private static RollbackOperationalSnapshotRequest rollbackRequest() {
        return new RollbackOperationalSnapshotRequest(
                COMMAND, GENERATION, "rollback/2026-10-11", EXPECTED);
    }

    @Test
    void publish_validCompletedPeriod_delegatesToStoreWithCanonicalActor() {
        var request = publishRequest();
        var expected = new OperationalPublicationDTO(
                COMMAND, GENERATION, EXPECTED,
                Instant.parse("2026-10-11T04:00:00Z"), "PUBLISH");
        when(store.publish(request, ACTOR)).thenReturn(expected);

        var actual = service.publish(request, ACTOR);

        assertThat(actual).isEqualTo(expected);
        verify(store).publish(request, ACTOR);
    }

    @Test
    void publish_exactly366CompletedDays_isAccepted() {
        var valid = publishRequest();
        var request = new PublishOperationalSnapshotRequest(
                valid.commandId(), valid.generationId(), valid.manifestFingerprint(),
                LocalDate.of(2025, 10, 11), LocalDate.of(2026, 10, 11),
                valid.acceptanceReference(), null);
        var expected = new OperationalPublicationDTO(
                request.commandId(), request.generationId(), null,
                Instant.parse("2026-10-11T04:00:00Z"), "PUBLISH");
        when(store.publish(request, ACTOR)).thenReturn(expected);

        assertThat(service.publish(request, ACTOR)).isEqualTo(expected);
        verify(store).publish(request, ACTOR);
    }

    @Test
    void publish_missingExpectedCurrentGenerationId_isAllowedForInitialPublication() {
        var valid = publishRequest();
        var request = new PublishOperationalSnapshotRequest(
                valid.commandId(), valid.generationId(), valid.manifestFingerprint(), valid.coveredFrom(),
                valid.coveredTo(), valid.acceptanceReference(), null);
        when(store.publish(request, ACTOR)).thenReturn(
                new OperationalPublicationDTO(COMMAND, GENERATION, null,
                        Instant.parse("2026-10-11T04:00:00Z"), "PUBLISH"));

        service.publish(request, ACTOR);

        verify(store).publish(request, ACTOR);
    }

    @Test
    void publish_nullRequest_rejectsBeforeStore() {
        assertInvalid(() -> service.publish(null, ACTOR));
        verifyNoInteractions(store);
    }

    @Test
    void publish_missingCommandId_rejectsBeforeStore() {
        var valid = publishRequest();
        assertInvalid(() -> service.publish(new PublishOperationalSnapshotRequest(
                null, valid.generationId(), valid.manifestFingerprint(), valid.coveredFrom(),
                valid.coveredTo(), valid.acceptanceReference(), valid.expectedCurrentGenerationId()), ACTOR));
        verifyNoInteractions(store);
    }

    @Test
    void publish_missingGenerationId_rejectsBeforeStore() {
        var valid = publishRequest();
        assertInvalid(() -> service.publish(new PublishOperationalSnapshotRequest(
                valid.commandId(), null, valid.manifestFingerprint(), valid.coveredFrom(),
                valid.coveredTo(), valid.acceptanceReference(), valid.expectedCurrentGenerationId()), ACTOR));
        verifyNoInteractions(store);
    }

    @Test
    void publish_missingManifestFingerprint_rejectsBeforeStore() {
        var valid = publishRequest();
        assertInvalid(() -> service.publish(new PublishOperationalSnapshotRequest(
                valid.commandId(), valid.generationId(), null, valid.coveredFrom(),
                valid.coveredTo(), valid.acceptanceReference(), valid.expectedCurrentGenerationId()), ACTOR));
        verifyNoInteractions(store);
    }

    @Test
    void publish_uppercaseOrNonSha256Manifest_rejectsBeforeStore() {
        var valid = publishRequest();
        assertInvalid(() -> service.publish(new PublishOperationalSnapshotRequest(
                valid.commandId(), valid.generationId(), "A".repeat(64), valid.coveredFrom(),
                valid.coveredTo(), valid.acceptanceReference(), valid.expectedCurrentGenerationId()), ACTOR));
        verifyNoInteractions(store);
    }

    @Test
    void publish_nonHexManifest_rejectsBeforeStore() {
        var valid = publishRequest();
        assertInvalid(() -> service.publish(new PublishOperationalSnapshotRequest(
                valid.commandId(), valid.generationId(), "g".repeat(64), valid.coveredFrom(),
                valid.coveredTo(), valid.acceptanceReference(), valid.expectedCurrentGenerationId()), ACTOR));
        verifyNoInteractions(store);
    }

    @Test
    void publish_wrongManifestLength_rejectsBeforeStore() {
        var valid = publishRequest();
        assertInvalid(() -> service.publish(new PublishOperationalSnapshotRequest(
                valid.commandId(), valid.generationId(), "a".repeat(63), valid.coveredFrom(),
                valid.coveredTo(), valid.acceptanceReference(), valid.expectedCurrentGenerationId()), ACTOR));
        verifyNoInteractions(store);
    }

    @Test
    void publish_missingCoveredFrom_rejectsBeforeStore() {
        var valid = publishRequest();
        assertInvalid(() -> service.publish(new PublishOperationalSnapshotRequest(
                valid.commandId(), valid.generationId(), valid.manifestFingerprint(), null,
                valid.coveredTo(), valid.acceptanceReference(), valid.expectedCurrentGenerationId()), ACTOR));
        verifyNoInteractions(store);
    }

    @Test
    void publish_missingCoveredTo_rejectsBeforeStore() {
        var valid = publishRequest();
        assertInvalid(() -> service.publish(new PublishOperationalSnapshotRequest(
                valid.commandId(), valid.generationId(), valid.manifestFingerprint(), valid.coveredFrom(),
                null, valid.acceptanceReference(), valid.expectedCurrentGenerationId()), ACTOR));
        verifyNoInteractions(store);
    }

    @Test
    void publish_reversedPeriod_rejectsBeforeStore() {
        var valid = publishRequest();
        assertInvalid(() -> service.publish(new PublishOperationalSnapshotRequest(
                valid.commandId(), valid.generationId(), valid.manifestFingerprint(),
                LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 10),
                valid.acceptanceReference(), valid.expectedCurrentGenerationId()), ACTOR));
        verifyNoInteractions(store);
    }

    @Test
    void publish_periodLongerThan366Days_rejectsBeforeStore() {
        var valid = publishRequest();
        assertInvalid(() -> service.publish(new PublishOperationalSnapshotRequest(
                valid.commandId(), valid.generationId(), valid.manifestFingerprint(),
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2),
                valid.acceptanceReference(), valid.expectedCurrentGenerationId()), ACTOR));
        verifyNoInteractions(store);
    }

    @Test
    void publish_periodBeforeYear2000_rejectsBeforeStore() {
        var valid = publishRequest();
        assertInvalid(() -> service.publish(new PublishOperationalSnapshotRequest(
                valid.commandId(), valid.generationId(), valid.manifestFingerprint(),
                LocalDate.of(1999, 12, 31), LocalDate.of(2000, 1, 1),
                valid.acceptanceReference(), valid.expectedCurrentGenerationId()), ACTOR));
        verifyNoInteractions(store);
    }

    @Test
    void publish_periodAfterYear2100_rejectsBeforeStore() {
        var valid = publishRequest();
        assertInvalid(() -> service.publish(new PublishOperationalSnapshotRequest(
                valid.commandId(), valid.generationId(), valid.manifestFingerprint(),
                LocalDate.of(2100, 12, 31), LocalDate.of(2101, 1, 1),
                valid.acceptanceReference(), valid.expectedCurrentGenerationId()), ACTOR));
        verifyNoInteractions(store);
    }

    @Test
    void publish_futurePeriod_rejectsBeforeStore() {
        var valid = publishRequest();
        assertInvalid(() -> service.publish(new PublishOperationalSnapshotRequest(
                valid.commandId(), valid.generationId(), valid.manifestFingerprint(),
                LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 12),
                valid.acceptanceReference(), valid.expectedCurrentGenerationId()), ACTOR));
        verifyNoInteractions(store);
    }

    @Test
    void publish_missingAcceptanceReference_rejectsBeforeStore() {
        var valid = publishRequest();
        assertInvalid(() -> service.publish(new PublishOperationalSnapshotRequest(
                valid.commandId(), valid.generationId(), valid.manifestFingerprint(), valid.coveredFrom(),
                valid.coveredTo(), null, valid.expectedCurrentGenerationId()), ACTOR));
        verifyNoInteractions(store);
    }

    @Test
    void publish_referenceOutsideExactOperatorPattern_rejectsBeforeStore() {
        var valid = publishRequest();
        assertInvalid(() -> service.publish(new PublishOperationalSnapshotRequest(
                valid.commandId(), valid.generationId(), valid.manifestFingerprint(), valid.coveredFrom(),
                valid.coveredTo(), " approval with spaces ", valid.expectedCurrentGenerationId()), ACTOR));
        verifyNoInteractions(store);
    }

    @Test
    void publish_referenceStartingWithPunctuation_rejectsBeforeStore() {
        var valid = publishRequest();
        assertInvalid(() -> service.publish(new PublishOperationalSnapshotRequest(
                valid.commandId(), valid.generationId(), valid.manifestFingerprint(), valid.coveredFrom(),
                valid.coveredTo(), "/approval-2026", valid.expectedCurrentGenerationId()), ACTOR));
        verifyNoInteractions(store);
    }

    @Test
    void publish_referenceLongerThan120Characters_rejectsBeforeStore() {
        var valid = publishRequest();
        assertInvalid(() -> service.publish(new PublishOperationalSnapshotRequest(
                valid.commandId(), valid.generationId(), valid.manifestFingerprint(), valid.coveredFrom(),
                valid.coveredTo(), "a".repeat(121), valid.expectedCurrentGenerationId()), ACTOR));
        verifyNoInteractions(store);
    }

    @Test
    void publish_missingActor_rejectsBeforeStore() {
        assertInvalid(() -> service.publish(publishRequest(), null));
        verifyNoInteractions(store);
    }

    @Test
    void rollback_validExpectedPointer_delegatesToStoreWithCanonicalActor() {
        var request = rollbackRequest();
        var expected = new OperationalPublicationDTO(
                COMMAND, GENERATION, EXPECTED,
                Instant.parse("2026-10-11T04:00:00Z"), "ROLLBACK");
        when(store.rollback(request, ACTOR)).thenReturn(expected);

        var actual = service.rollback(request, ACTOR);

        assertThat(actual).isEqualTo(expected);
        verify(store).rollback(request, ACTOR);
    }

    @Test
    void rollback_nullRequest_rejectsBeforeStore() {
        assertInvalid(() -> service.rollback(null, ACTOR));
        verifyNoInteractions(store);
    }

    @Test
    void rollback_missingExpectedCurrentGenerationId_rejectsBeforeStore() {
        var valid = rollbackRequest();
        assertInvalid(() -> service.rollback(new RollbackOperationalSnapshotRequest(
                valid.commandId(), valid.generationId(), valid.acceptanceReference(), null), ACTOR));
        verifyNoInteractions(store);
    }

    @Test
    void rollback_missingCommandId_rejectsBeforeStore() {
        var valid = rollbackRequest();
        assertInvalid(() -> service.rollback(new RollbackOperationalSnapshotRequest(
                null, valid.generationId(), valid.acceptanceReference(), valid.expectedCurrentGenerationId()), ACTOR));
        verifyNoInteractions(store);
    }

    @Test
    void rollback_missingGenerationId_rejectsBeforeStore() {
        var valid = rollbackRequest();
        assertInvalid(() -> service.rollback(new RollbackOperationalSnapshotRequest(
                valid.commandId(), null, valid.acceptanceReference(), valid.expectedCurrentGenerationId()), ACTOR));
        verifyNoInteractions(store);
    }

    @Test
    void rollback_missingAcceptanceReference_rejectsBeforeStore() {
        var valid = rollbackRequest();
        assertInvalid(() -> service.rollback(new RollbackOperationalSnapshotRequest(
                valid.commandId(), valid.generationId(), null, valid.expectedCurrentGenerationId()), ACTOR));
        verifyNoInteractions(store);
    }

    @Test
    void rollback_referenceOutsideExactOperatorPattern_rejectsBeforeStore() {
        var valid = rollbackRequest();
        assertInvalid(() -> service.rollback(new RollbackOperationalSnapshotRequest(
                valid.commandId(), valid.generationId(), "rollback with spaces", valid.expectedCurrentGenerationId()), ACTOR));
        verifyNoInteractions(store);
    }

    @Test
    void rollback_missingActor_rejectsBeforeStore() {
        assertInvalid(() -> service.rollback(rollbackRequest(), null));
        verifyNoInteractions(store);
    }

    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(ReportRuleException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("OPERATIONAL_PUBLICATION_INVALID"));
    }
}
