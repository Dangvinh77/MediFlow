package com.mediflow.report.application.mapper;

import com.mediflow.report.domain.model.OperationalContribution.Metric;
import com.mediflow.report.support.AdmissionEvidenceTestFixtures;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AdmissionStartedContributionMapperTest {
    private final AdmissionStartedContributionMapper mapper = new AdmissionStartedContributionMapper(ZoneId.of("Asia/Bangkok"));
    @Test void map_actualProducerStartCountsOneExactEpisodeAndStartingDepartment() throws Exception {
        var source = AdmissionEvidenceTestFixtures.fixture("admission.started");
        var command = mapper.map(source); assertThat(command.event()).isEqualTo(source);
        assertThat(command.contributions()).singleElement().satisfies(fact -> {
            assertThat(fact.metric()).isEqualTo(Metric.ADMISSIONS); assertThat(fact.sourceRevision()).isOne();
            assertThat(fact.sourceId()).isEqualTo(source.metadata().sourceId()); assertThat(fact.careEpisodeId()).isEqualTo(fact.sourceId());
            assertThat(fact.value()).isEqualByComparingTo("1"); assertThat(fact.category()).isNull();
        });
    }
    @Test void map_closeCannotBecomeAdmissionOrMedicalDischarge() throws Exception {
        var source = AdmissionEvidenceTestFixtures.fixture("admission.closed");
        assertThatThrownBy(() -> mapper.map(source)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void map_businessDateUsesAdmittedAtAndConfiguredZoneNotArrivalTime() throws Exception {
        var source = AdmissionEvidenceTestFixtures.fixture("admission.started");
        var changed = AdmissionEvidenceTestFixtures.changed(source, UUID.randomUUID(), "admittedAt", "2026-09-28T18:00:00.123456789Z");
        var fact = mapper.map(changed).contributions().getFirst();
        assertThat(fact.metricDate().toString()).isEqualTo("2026-09-29");
        assertThat(fact.occurredAt().toString()).isEqualTo("2026-09-28T18:00:00.123456789Z");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"sourceRevision", "revision", "supersedesEventId", "correctionOf"})
    void map_unsupportedCorrectionIsNotRecastAsNewAdmission(String marker) throws Exception {
        var source = AdmissionEvidenceTestFixtures.fixture("admission.started");
        var changed = AdmissionEvidenceTestFixtures.changed(source, UUID.randomUUID(), marker, "1");
        assertThatThrownBy(() -> mapper.map(changed)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("corrections");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"admissionId", "patientId", "departmentId", "bedId", "emergencyOverrideId"})
    void map_shortUuidCannotBecomeCanonicalIdentityOrAnAdmissionCount(String field) throws Exception {
        var source = AdmissionEvidenceTestFixtures.fixture("admission.started");
        var changed = AdmissionEvidenceTestFixtures.changed(source, UUID.randomUUID(), field, "0-0-0-0-1");
        assertThatThrownBy(() -> mapper.map(changed)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Canonical");
    }
}
