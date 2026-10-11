package com.mediflow.report.application.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.application.port.out.FinancialSnapshotReadPort;
import com.mediflow.report.application.service.ReportCapabilityApplicationService;
import com.mediflow.report.infrastructure.messaging.carefinance.CareFinanceEnvelopeDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Consumer contract tests use the producer-owned fixture bytes.  These tests intentionally do not
 * recreate producer DTOs: the capability registry must agree with the bytes that the mappers
 * actually accept, and unsupported revisions must fail closed.
 */
class ReportSupportedSourceContractTest {

    private static final Map<String, String> FIXTURES = Map.ofEntries(
            Map.entry("medicalrecord.completed", "../clinical-service/src/test/resources/contracts/medicalrecord.completed.v1.json"),
            Map.entry("lab.result.created", "../lab-service/src/test/resources/contracts/lab.result.created.v1.json"),
            Map.entry("prescription.filled", "../pharmacy-service/src/test/resources/contracts/care-finance-v1/prescription.filled.v1.json"),
            Map.entry("surgery.completed", "../surgery-service/src/test/resources/contracts/surgery-outcomes-v1/surgery.completed.outpatient.v1.json"),
            Map.entry("surgery.cancelled", "../surgery-service/src/test/resources/contracts/surgery-outcomes-v1/surgery.cancelled.outpatient.v1.json"),
            Map.entry("admission.started", "../inpatient-service/src/test/resources/contracts/admission.started.v1.json"),
            Map.entry("admission.closed", "../inpatient-service/src/test/resources/contracts/admission.closed.v1.json"),
            Map.entry("discharge.medically.approved", "../inpatient-service/src/test/resources/contracts/discharge.medically.approved.v1.json"),
            Map.entry("payment.completed", "../billing-service/src/test/resources/contracts/ledger-v1/payment-prescription-first.json"),
            Map.entry("payment.refunded", "../billing-service/src/test/resources/contracts/ledger-v1/refund-service.json"),
            Map.entry("settlement.reconciled", "../billing-service/src/test/resources/contracts/ledger-v1/settlement-reconciled-1.json"),
            Map.entry("settlement.recognition.completed", "../billing-service/src/test/resources/contracts/ledger-v1/settlement-recognition-completed.json"));

    private final ObjectMapper json = new ObjectMapper();
    private final CareFinanceEnvelopeDecoder decoder = new CareFinanceEnvelopeDecoder(json);
    private final ReportCapabilityApplicationService service =
            new ReportCapabilityApplicationService(mock(FinancialSnapshotReadPort.class));

    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Bangkok");

    private record OperationalFixture(String eventType, String path,
            Function<DecodedCareFinanceEvent, ?> mapper) {
    }

    private static Stream<OperationalFixture> operationalFixtures() {
        return Stream.of(
                new OperationalFixture("medicalrecord.completed", FIXTURES.get("medicalrecord.completed"),
                        event -> new ClinicalOperationalContributionMapper(REPORT_ZONE).map(event)),
                new OperationalFixture("lab.result.created", FIXTURES.get("lab.result.created"),
                        event -> new LabOperationalContributionMapper(REPORT_ZONE).map(event)),
                new OperationalFixture("prescription.filled", FIXTURES.get("prescription.filled"),
                        event -> new PrescriptionOperationalContributionMapper(REPORT_ZONE).map(event)),
                new OperationalFixture("surgery.completed", FIXTURES.get("surgery.completed"),
                        event -> new SurgeryOperationalContributionMapper(REPORT_ZONE).map(event)),
                new OperationalFixture("surgery.cancelled", FIXTURES.get("surgery.cancelled"),
                        event -> new SurgeryOperationalContributionMapper(REPORT_ZONE).map(event)),
                new OperationalFixture("admission.started", FIXTURES.get("admission.started"),
                        event -> new AdmissionReportFactMapper().map(event)),
                new OperationalFixture("admission.closed", FIXTURES.get("admission.closed"),
                        event -> new AdmissionReportFactMapper().map(event)),
                new OperationalFixture("discharge.medically.approved", FIXTURES.get("discharge.medically.approved"),
                        event -> new MedicalDischargeMapper(REPORT_ZONE).fact(event)));
    }

    private static Stream<Arguments> operationalCorrectionMarkers() {
        return operationalFixtures().flatMap(fixture -> Stream.of(
                "supersedesOperationId", "correctionOf", "replacementOf", "supersedesEventId", "correctedAt", "revision"
        ).map(marker -> Arguments.of(fixture, marker)));
    }

    private static Stream<Arguments> cashCorrectionMarkers() {
        return Stream.of(
                Arguments.of("payment.completed", FIXTURES.get("payment.completed"), "supersedesOperationId"),
                Arguments.of("payment.refunded", FIXTURES.get("payment.refunded"), "correctionOf"));
    }

    @Test
    void capabilities_matchProducerFixtureEventIdentityAndVersion() throws Exception {
        var capabilities = service.capabilities();

        for (var entry : FIXTURES.entrySet()) {
            var event = decoder.decode(entry.getKey(), Files.readAllBytes(Path.of(entry.getValue())));
            var declared = capabilities.sources().stream()
                    .filter(source -> source.eventType().equals(entry.getKey()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No capability for " + entry.getKey()));

            assertThat(declared.producer()).isEqualTo(event.metadata().producer());
            assertThat(declared.envelopeVersion()).isEqualTo(event.metadata().version());
            assertThat(declared.sourceIdField()).isEqualTo(event.metadata().sourceField());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("operationalFixtures")
    void actualOperationalProducerFixture_mapsThroughItsSupportedMapper(OperationalFixture fixture) throws Exception {
        var event = decoder.decode(fixture.eventType(), Files.readAllBytes(Path.of(fixture.path())));

        assertThat(fixture.mapper().apply(event))
                .as("accepted first-operation fixture for %s", fixture.eventType())
                .isNotNull();
    }

    @ParameterizedTest(name = "{0} rejects {1}")
    @MethodSource("operationalCorrectionMarkers")
    void operationalProducerFixture_withUnsupportedCorrectionMarker_isRejectedBeforeContribution(
            OperationalFixture fixture, String marker) throws Exception {
        var original = decoder.decode(fixture.eventType(), Files.readAllBytes(Path.of(fixture.path())));
        var payload = new LinkedHashMap<>(original.payload());
        payload.put(marker, "unsupported-marker");
        var poisoned = new DecodedCareFinanceEvent(original.metadata(), payload);

        assertThatThrownBy(() -> fixture.mapper().apply(poisoned))
                .as("unsupported correction marker %s for %s", marker, fixture.eventType())
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void producerFixtureSet_isExactlyTheDeclaredSupportedSet() {
        assertThat(service.capabilities().sources())
                .extracting(source -> source.eventType())
                .containsExactlyInAnyOrderElementsOf(FIXTURES.keySet());
    }

    @Test
    void envelopeVersionTwo_isRejectedBeforeCapabilityCanAcceptIt() throws Exception {
        ObjectNode root = (ObjectNode) json.readTree(Files.readAllBytes(Path.of(
                FIXTURES.get("payment.completed"))));
        root.put("version", 2);

        assertThatThrownBy(() -> decoder.decode("payment.completed", json.writeValueAsBytes(root)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void surgerySourceRevisionTwo_isRejectedByCurrentMapper() throws Exception {
        ObjectNode root = (ObjectNode) json.readTree(Files.readAllBytes(Path.of(
                FIXTURES.get("surgery.completed"))));
        ((ObjectNode) root.path("payload")).put("sourceRevision", 2);
        var event = decoder.decode("surgery.completed", json.writeValueAsBytes(root));

        assertThatThrownBy(() -> new SurgeryOperationalContributionMapper(java.time.ZoneId.of("Asia/Bangkok"))
                .map(event))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.capabilities().unsupportedFeatures())
                .contains("POST_FINAL_EARNED_REVERSAL");
    }

    @ParameterizedTest(name = "{0} rejects {2}")
    @MethodSource("cashCorrectionMarkers")
    void cashProducerFixture_withUnknownCorrectionMarker_isRejectedBeforeFinancialEffect(
            String eventType, String path, String marker) throws Exception {
        ObjectNode root = (ObjectNode) json.readTree(Files.readAllBytes(Path.of(path)));
        ((ObjectNode) root.path("payload")).put(marker, "unsupported-marker");
        var event = decoder.decode(eventType, json.writeValueAsBytes(root));

        assertThatThrownBy(() -> {
            if ("payment.completed".equals(eventType)) {
                new BillingCashReceiptMapper(REPORT_ZONE).map(event);
            } else {
                new BillingCashRefundMapper(REPORT_ZONE).map(event);
            }
        }).isInstanceOf(RuntimeException.class);
    }

    @Test
    void settlementReconciliationFinalMarker_isRejectedByNonFinalMapper() throws Exception {
        ObjectNode root = (ObjectNode) json.readTree(Files.readAllBytes(Path.of(
                FIXTURES.get("settlement.reconciled"))));
        ((ObjectNode) root.path("payload")).put("finalSettlement", true);
        var event = decoder.decode("settlement.reconciled", json.writeValueAsBytes(root));

        assertThatThrownBy(() -> new BillingSettlementSnapshotMapper(java.time.ZoneId.of("Asia/Bangkok"))
                .map(event))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
