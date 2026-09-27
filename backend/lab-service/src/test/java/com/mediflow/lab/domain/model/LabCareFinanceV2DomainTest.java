package com.mediflow.lab.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.lab.domain.exception.LabRuleException;

class LabCareFinanceV2DomainTest {

    private static final LocalDate REQUESTED_DATE = LocalDate.of(2026, 9, 20);
    private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");

    @Test
    void create_v2Request_startsAwaitingPaymentWithExplicitEpisodeAndPrice() {
        UUID sourceOrderId = UUID.randomUUID();
        UUID episodeId = UUID.randomUUID();

        LabTest test = LabTest.createV2(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), sourceOrderId,
                CareEpisodeType.OUTPATIENT_VISIT, episodeId, "CBC", "LAB-CBC", REQUESTED_DATE);

        assertThat(test.getCareContractVersion()).isEqualTo(1);
        assertThat(test.getStatus()).isEqualTo(LabTestStatus.AWAITING_PAYMENT);
        assertThat(test.getCareEpisodeType()).isEqualTo(CareEpisodeType.OUTPATIENT_VISIT);
        assertThat(test.getCareEpisodeId()).isEqualTo(episodeId);
        assertThat(test.getSourceOrderId()).isEqualTo(sourceOrderId);
        assertThat(test.getPriceCode()).isEqualTo("LAB-CBC");
        assertThat(test.isPaid()).isFalse();
    }

    @Test
    void create_v2Request_missingEpisode_rejectsWithoutGuessingFromRecord() {
        assertThatThrownBy(() -> LabTest.createV2(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                CareEpisodeType.OUTPATIENT_VISIT, null, "CBC", "LAB-CBC", REQUESTED_DATE))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_EPISODE_REQUIRED");
    }

    @Test
    void start_awaitingPaymentWithoutClearanceOrOverride_rejects() {
        LabTest test = v2Test();

        assertThatThrownBy(() -> test.start(null, null, NOW))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_CLEARANCE_REQUIRED");
        assertThat(test.getStatus()).isEqualTo(LabTestStatus.AWAITING_PAYMENT);
    }

    @Test
    void grantClearance_wrongEpisode_rejectsExactTargetMismatch() {
        LabTest test = v2Test();
        LabFinancialClearance clearance = clearanceFor(test, CareEpisodeType.ADMISSION, UUID.randomUUID(), null);

        assertThatThrownBy(() -> test.grantClearance(clearance))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_CLEARANCE_TARGET_MISMATCH");
        assertThat(test.getStatus()).isEqualTo(LabTestStatus.AWAITING_PAYMENT);
    }

    @Test
    void start_expiredClearance_rejectsBeforeEnteringProgress() {
        LabTest test = v2Test();
        LabFinancialClearance clearance = clearanceFor(test, test.getCareEpisodeType(), test.getCareEpisodeId(),
                NOW);
        test.grantClearance(clearance);

        assertThatThrownBy(() -> test.start(clearance, null, NOW))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_CLEARANCE_EXPIRED");
        assertThat(test.getStatus()).isEqualTo(LabTestStatus.READY);
    }

    @Test
    void start_emergencyOverrideRecordsAuditAndDoesNotMarkPaid() {
        LabTest test = v2Test();
        LabEmergencyOverride override = overrideFor(test);

        test.start(null, override, NOW);

        assertThat(test.getStatus()).isEqualTo(LabTestStatus.IN_PROGRESS);
        assertThat(test.getEmergencyOverrideId()).isEqualTo(override.overrideId());
        assertThat(test.isPaid()).isFalse();
    }

    @Test
    void recordResults_v2AwaitingPayment_rejectsWithoutOperationalAuthorization() {
        LabTest test = v2Test();

        assertThatThrownBy(() -> test.recordResults(List.of(result()), "Normal", REQUESTED_DATE))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_CLEARANCE_REQUIRED");
    }

    @Test
    void recordResults_v2BeforeRequestedDate_rejectsAndKeepsProgressState() {
        LabTest test = v2Test();
        LabEmergencyOverride override = overrideFor(test);
        test.start(null, override, NOW);

        assertThatThrownBy(() -> test.recordResults(List.of(result()), "Normal", REQUESTED_DATE.minusDays(1)))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_DATE_BEFORE_REQUEST");
        assertThat(test.getStatus()).isEqualTo(LabTestStatus.IN_PROGRESS);
    }

    @Test
    void recordResults_v2CompletesOnceAndIncrementsImmutableSnapshotVersion() {
        LabTest test = v2Test();
        test.start(null, overrideFor(test), NOW);

        test.recordResults(List.of(result()), "Normal", REQUESTED_DATE);

        assertThat(test.getStatus()).isEqualTo(LabTestStatus.COMPLETED);
        assertThat(test.getResultVersion()).isEqualTo(1);
        assertThat(test.getResults()).hasSize(1);
    }

    @Test
    void recordResults_v2TerminalSnapshot_returnsConflictCode() {
        LabTest test = v2Test();
        test.start(null, overrideFor(test), NOW);
        test.recordResults(List.of(result()), "Normal", REQUESTED_DATE);

        assertThatThrownBy(() -> test.recordResults(List.of(result()), "Amended", REQUESTED_DATE))
                .isInstanceOf(DuplicateResourceException.class)
                .extracting(DuplicateResourceException.class::cast)
                .extracting(DuplicateResourceException::getCode)
                .isEqualTo("LAB_RESULT_FINALIZED");
    }

    @Test
    void changeStatus_v2CannotBypassGuardedStartEndpoint() {
        LabTest test = v2Test();

        assertThatThrownBy(() -> test.changeStatus(LabTestStatus.IN_PROGRESS))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_INVALID_STATUS_TRANSITION");
    }

    private static LabTest v2Test() {
        return LabTest.createV2(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), "CBC", "LAB-CBC", REQUESTED_DATE);
    }

    private static LabFinancialClearance clearanceFor(
            LabTest test, CareEpisodeType episodeType, UUID episodeId, Instant expiresAt) {
        return LabFinancialClearance.grant(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), test.getTestId(), test.getPatientId(), episodeType,
                episodeId, ClearancePurpose.LAB_TEST, new BigDecimal("250000.00"), "VND", expiresAt, false, NOW);
    }

    private static LabEmergencyOverride overrideFor(LabTest test) {
        return LabEmergencyOverride.approve(UUID.randomUUID(), test.getTestId(), test.getPatientId(),
                test.getCareEpisodeType(), test.getCareEpisodeId(), UUID.randomUUID(), "DOCTOR",
                "Urgent diagnostic care", NOW);
    }

    private static LabResult result() {
        return LabResult.create("WBC", "7.5", "10^9/L", "4.0-10.0");
    }
}
