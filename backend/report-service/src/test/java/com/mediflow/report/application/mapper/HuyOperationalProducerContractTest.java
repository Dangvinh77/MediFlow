package com.mediflow.report.application.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.domain.model.OperationalContribution.Metric;
import com.mediflow.report.infrastructure.messaging.carefinance.CareFinanceEnvelopeDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class HuyOperationalProducerContractTest {
    private final CareFinanceEnvelopeDecoder decoder=new CareFinanceEnvelopeDecoder(new ObjectMapper());
    private final SurgeryOperationalContributionMapper surgery=new SurgeryOperationalContributionMapper(ZoneId.of("Asia/Bangkok"));
    private final PrescriptionOperationalContributionMapper pharmacy=new PrescriptionOperationalContributionMapper(ZoneId.of("Asia/Bangkok"));

    @Test void clinical_actualCompletionBytes_countsVisitWithoutInventingAdmissionOrEpisode() throws Exception {
        var event = decoder.decode("medicalrecord.completed", Files.readAllBytes(Path.of(
                "../clinical-service/src/test/resources/contracts/medicalrecord.completed.v1.json")));
        var mapper = new ClinicalOperationalContributionMapper(ZoneId.of("Asia/Bangkok"));
        var fact = mapper.map(event).contributions().getFirst();
        assertThat(fact.metric()).isEqualTo(Metric.COMPLETED_VISITS);
        assertThat(fact.category()).isEqualTo("ADMISSION");
        assertThat(fact.careEpisodeId()).isNull();
        assertThat(fact.sourceRevision()).isOne();
        var payload = new LinkedHashMap<>(event.payload()); payload.put("admissionRequired", false);
        assertThatThrownBy(() -> mapper.map(new DecodedCareFinanceEvent(event.metadata(), payload)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest @ValueSource(strings={"admission","outpatient"})
    void completed_actualProducerBytes_mapsOneCountAndWholeActualMinutes(String mode) throws Exception {
        var event=surgery("completed",mode);
        var facts=surgery.map(event).contributions();
        assertThat(facts).extracting(f->f.metric()).containsExactly(Metric.SURGERIES_COMPLETED,Metric.SURGERY_DURATION_MINUTES);
        assertThat(facts).extracting(f->f.value().longValueExact()).containsExactly(1L,6L);
        assertThat(facts).allSatisfy(f->{assertThat(f.sourceId()).isEqualTo(event.metadata().sourceId());
            assertThat(f.sourceRevision()).isOne();assertThat(f.metricDate()).isEqualTo(LocalDate.of(2026,10,7));assertThat(f.category()).isNull();});
    }
    @ParameterizedTest @ValueSource(strings={"admission","outpatient"})
    void cancelled_actualProducerBytes_usesCancellationOperationNotDeliveryId(String mode) throws Exception {
        var event=surgery("cancelled",mode);
        var fact=surgery.map(event).contributions().getFirst();
        assertThat(fact.metric()).isEqualTo(Metric.SURGERIES_CANCELLED);
        assertThat(fact.sourceId()).isEqualTo(event.metadata().sourceId()).isNotEqualTo(event.metadata().eventId());
        assertThat(fact.category()).isEqualTo("BEFORE_START");
    }
    @ParameterizedTest @ValueSource(strings={"resultId","sourceRevision","careEpisodeId","patientId","departmentId","startedAt","completedAt","recordedAt"})
    void completed_missingRequiredField_hasNoFallback(String missing) throws Exception {
        var event=surgery("completed","admission");
        var payload=new LinkedHashMap<>(event.payload());payload.remove(missing);
        assertThatThrownBy(()->surgery.map(new DecodedCareFinanceEvent(event.metadata(),payload))).isInstanceOf(RuntimeException.class);
    }
    @Test void completed_unknownCorrectionOrAdmissionMismatch_isRejected() throws Exception {
        var event=surgery("completed","admission");
        var payload=new LinkedHashMap<>(event.payload());payload.put("sourceRevision",2);
        var correction=new DecodedCareFinanceEvent(event.metadata(),payload);
        assertThatThrownBy(()->surgery.map(correction)).isInstanceOf(IllegalArgumentException.class);
        payload=new LinkedHashMap<>(event.payload());payload.put("admissionId","00000000-0000-4000-8000-000000000099");
        var wrongEpisode=new DecodedCareFinanceEvent(event.metadata(),payload);
        assertThatThrownBy(()->surgery.map(wrongEpisode)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void pharmacy_actualHeldFilledFixture_mapsDispenseOnceAndExplicitUnitSum() throws Exception {
        var path=Path.of("../pharmacy-service/src/test/resources/contracts/care-finance-v1/prescription.filled.v1.json");
        var event=decoder.decode("prescription.filled",Files.readAllBytes(path));
        var facts=pharmacy.map(event).contributions();
        assertThat(facts).extracting(f->f.metric()).containsExactly(Metric.DISPENSED_PRESCRIPTIONS,Metric.DISPENSED_UNITS);
        assertThat(facts).extracting(f->f.value().longValueExact()).containsExactly(1L,2L);
        assertThat(facts).allSatisfy(f->assertThat(f.sourceRevision()).isOne());
    }
    private DecodedCareFinanceEvent surgery(String type,String mode) throws Exception {
        var path=Path.of("../surgery-service/src/test/resources/contracts/surgery-outcomes-v1/surgery."+type+"."+mode+".v1.json");
        return decoder.decode("surgery."+type,Files.readAllBytes(path));
    }
}
