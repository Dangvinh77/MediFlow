package com.mediflow.report.application.mapper;

import com.mediflow.report.domain.model.AdmissionReportHistory;
import com.mediflow.report.domain.model.OperationalContribution.Metric;
import com.mediflow.report.support.AdmissionEvidenceTestFixtures;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MedicalDischargeMapperTest {
    private final MedicalDischargeMapper mapper = new MedicalDischargeMapper(ZoneId.of("Asia/Bangkok"));
    private AdmissionReportHistory history() throws Exception {
        var fact = new AdmissionReportFactMapper().map(AdmissionEvidenceTestFixtures.fixture("admission.started"));
        return new AdmissionReportHistory(fact.admissionId(), fact, null);
    }
    @Test void actualProducerProof_generatesMedicalCountAndFlooredCompletedStayDuration() throws Exception {
        var command = mapper.contributions(AdmissionEvidenceTestFixtures.fixture("discharge.medically.approved"), history());
        assertThat(command.contributions()).extracting(f -> f.metric()).containsExactly(Metric.MEDICAL_DISCHARGES, Metric.MEDICAL_LOS_MINUTES, Metric.COMPLETED_STAY_DAYS);
        assertThat(command.contributions()).extracting(f -> f.value().longValueExact()).containsExactly(1L, 3290L, 2L);
        var departmentId = history().departmentId();
        assertThat(command.contributions()).allSatisfy(f -> {
            assertThat(f.metricDate().toString()).isEqualTo("2026-09-30");
            assertThat(f.departmentId()).isEqualTo(departmentId);
        });
    }
    @Test void administrativeClose_neverCountsAsMedicalDischarge() throws Exception {
        assertThatThrownBy(() -> mapper.fact(AdmissionEvidenceTestFixtures.fixture("admission.closed"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void medicalProofWithoutExactStart_doesNotInventDepartmentOrDuration() throws Exception {
        var event = AdmissionEvidenceTestFixtures.fixture("discharge.medically.approved");
        assertThatThrownBy(() -> mapper.contributions(event, new AdmissionReportHistory(event.metadata().sourceId(), null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void changedPatient_orChronology_rejects() throws Exception {
        var event = AdmissionEvidenceTestFixtures.fixture("discharge.medically.approved");
        assertThatThrownBy(() -> mapper.contributions(AdmissionEvidenceTestFixtures.changed(event, UUID.randomUUID(), "patientId", UUID.randomUUID().toString()), history()))
                .hasMessageContaining("chronology");
    }
    @Test void correctionOrNarrativeFields_requireNewSourceContract() throws Exception {
        var event = AdmissionEvidenceTestFixtures.fixture("discharge.medically.approved");
        for (String field : new String[]{"sourceRevision", "supersedesId", "diagnosis", "unknown"})
            assertThatThrownBy(() -> mapper.fact(AdmissionEvidenceTestFixtures.changed(event, UUID.randomUUID(), field, "not accepted")))
                    .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void noncanonicalProofUuid_rejectsBeforeEffects() throws Exception {
        var event = AdmissionEvidenceTestFixtures.fixture("discharge.medically.approved");
        assertThatThrownBy(() -> mapper.fact(AdmissionEvidenceTestFixtures.changed(event, UUID.randomUUID(), "summaryId", "1-1-1-1-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void duration_floorsEachCompletedStayAtExactNanosecondBoundaryNotRoundedDatabaseTime() throws Exception {
        var event = AdmissionEvidenceTestFixtures.fixture("discharge.medically.approved");
        var start = history();
        var at = start.start().businessAt().plusSeconds(86400).minusNanos(1);
        var p = new java.util.LinkedHashMap<>(event.payload()); p.put("approvedAt", at.toString());
        var m = event.metadata();
        var nearDay = new com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent(
                new com.mediflow.report.application.dto.command.carefinance.CareFinanceEventMetadata(m.eventId(),m.eventType(),m.version(),at,m.correlationId(),m.producer(),m.sourceField(),m.sourceId()),p);
        assertThat(mapper.contributions(nearDay,start).contributions()).extracting(f->f.value().longValueExact()).containsExactly(1L,1439L,0L);
        at = at.plusNanos(1); p.put("approvedAt",at.toString());
        var exactDay = new com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent(
                new com.mediflow.report.application.dto.command.carefinance.CareFinanceEventMetadata(m.eventId(),m.eventType(),m.version(),at,m.correlationId(),m.producer(),m.sourceField(),m.sourceId()),p);
        assertThat(mapper.contributions(exactDay,start).contributions()).extracting(f->f.value().longValueExact()).containsExactly(1L,1440L,1L);
    }
    @Test void medicalApprovalBeforeAdmission_isAConflictNotNegativeStayOrSilentZero() throws Exception {
        var fact = mapper.fact(AdmissionEvidenceTestFixtures.fixture("discharge.medically.approved"));
        var earlier = new com.mediflow.report.domain.model.MedicalDischargeFact(fact.admissionId(),fact.patientId(),fact.summaryId(),fact.approvedBy(),history().start().businessAt().minusNanos(1));
        assertThatThrownBy(()->earlier.elapsed(history())).hasMessageContaining("chronology");
    }
    @Test void exactMedicalDate_usesConfiguredZoneNeverDeliveryDate() throws Exception {
        var event = AdmissionEvidenceTestFixtures.fixture("discharge.medically.approved");
        var at = java.time.Instant.parse("2026-09-30T18:00:00Z");
        var payload = new java.util.LinkedHashMap<>(event.payload()); payload.put("approvedAt",at.toString());
        var m=event.metadata();
        var late = new com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent(
                new com.mediflow.report.application.dto.command.carefinance.CareFinanceEventMetadata(m.eventId(),m.eventType(),1,at,m.correlationId(),m.producer(),m.sourceField(),m.sourceId()),payload);
        assertThat(mapper.contributions(late,history()).contributions()).allSatisfy(f->assertThat(f.metricDate().toString()).isEqualTo("2026-10-01"));
    }
}
