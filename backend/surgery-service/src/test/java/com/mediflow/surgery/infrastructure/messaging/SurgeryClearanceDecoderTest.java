package com.mediflow.surgery.infrastructure.messaging;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.surgery.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SurgeryClearanceDecoderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SurgeryClearanceDecoder decoder = new SurgeryClearanceDecoder(mapper);
    private final Instant at = Instant.parse("2026-10-05T08:01:00Z");

    @ParameterizedTest @ValueSource(strings = {"exam","lab","prescription","admission"})
    void validOtherPurposeBillingFixturesAreNotApplicableWithoutSurgeryEffect(String purpose) throws Exception {
        byte[] source = Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/clearance-" + purpose + ".json"));
        assertThat(decoder.decodeApplicable("financial.clearance.granted",source,at)).isEmpty();
    }

    @Test void malformedOtherPurposeDoesNotBecomeAnAcknowledgedNotApplicableFact() throws Exception {
        var root = (ObjectNode) mapper.readTree(Files.readAllBytes(Path.of(
                "../billing-service/src/test/resources/contracts/ledger-v1/clearance-exam.json")));
        ((ObjectNode)root.path("payload")).remove("appointmentId");
        ((ObjectNode)root.path("payload")).remove("recordId");
        byte[] malformed = mapper.writeValueAsBytes(root);
        assertThatThrownBy(() -> decoder.decodeApplicable("financial.clearance.granted",malformed,at))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void exactBillingFixtureAuthorizesOnlyFinancialGuardForMatchingCase() throws Exception {
        var command = decoder.decode("financial.clearance.granted", fixture(), at);
        var clearance = command.clearance();
        assertThat(clearance.isValidFor(surgeryCase(clearance.patientId()), at)).isTrue();
        assertThat(clearance.isValidFor(surgeryCase(UUID.randomUUID()), at)).isFalse();
        assertThat(clearance.isValidFor(surgeryCase(clearance.patientId()), clearance.grantedAt().minusNanos(1))).isFalse();
        assertThat(command.incoming().producer()).isEqualTo("billing-service");
    }

    @Test void expiryUsesExactNanosecondsAndExclusiveUpperBound() throws Exception {
        var root = (ObjectNode) mapper.readTree(fixture());
        ((ObjectNode) root.path("payload")).put("expiresAt", "2026-10-05T08:01:00.123456789Z");
        var clearance = decoder.decode("financial.clearance.granted", mapper.writeValueAsBytes(root), at).clearance();
        var surgeryCase = surgeryCase(clearance.patientId());
        assertThat(clearance.isValidFor(surgeryCase, clearance.expiresAt().minusNanos(1))).isTrue();
        assertThat(clearance.isValidFor(surgeryCase, clearance.expiresAt())).isFalse();
    }

    @ParameterizedTest @ValueSource(strings = {"appointmentId", "recordId", "prescriptionId"})
    void unrelatedTargetCannotActAsFallback(String field) throws Exception {
        var root = (ObjectNode) mapper.readTree(fixture());
        ((ObjectNode) root.path("payload")).put(field, UUID.randomUUID().toString());
        reject(root);
    }

    @Test void wrongPurposeProducerVersionAdmissionOrEmergencyFailsClosed() throws Exception {
        var root = (ObjectNode) mapper.readTree(fixture()); root.put("producer", "inpatient-service"); reject(root);
        root = (ObjectNode) mapper.readTree(fixture()); root.put("version", 4294967297L); reject(root);
        root = (ObjectNode) mapper.readTree(fixture()); ((ObjectNode) root.path("payload")).put("purpose", "EXAM"); reject(root);
        root = (ObjectNode) mapper.readTree(fixture()); ((ObjectNode) root.path("payload")).put("admissionId", UUID.randomUUID().toString()); reject(root);
        root = (ObjectNode) mapper.readTree(fixture()); ((ObjectNode) root.path("payload")).put("emergencyOverride", true); reject(root);
        root = (ObjectNode) mapper.readTree(fixture()); ((ObjectNode) root.path("payload")).put("amount", "100"); reject(root);
    }

    @Test void newEventForSameImmutableGrantHasSeparateEventIdentityButSameBusinessFingerprint() throws Exception {
        var root = (ObjectNode) mapper.readTree(fixture());
        var first = decoder.decode("financial.clearance.granted", mapper.writeValueAsBytes(root), at);
        root.put("eventId", UUID.randomUUID().toString());
        var replay = decoder.decode("financial.clearance.granted", mapper.writeValueAsBytes(root), at);
        assertThat(replay.clearance().fingerprint()).isEqualTo(first.clearance().fingerprint());
        assertThat(replay.incoming().fingerprint()).isNotEqualTo(first.incoming().fingerprint());
    }
    @Test void abbreviatedUuidIsMalformedNotANormalizedTarget() throws Exception {
        var root = (ObjectNode) mapper.readTree(fixture());
        ((ObjectNode) root.path("payload")).put("surgeryCaseId", "0-0-0-0-4");
        reject(root);
    }
    private void reject(ObjectNode root) throws Exception {
        byte[] bytes = mapper.writeValueAsBytes(root);
        assertThatThrownBy(() -> decoder.decode("financial.clearance.granted", bytes, at)).isInstanceOf(IllegalArgumentException.class);
    }
    private SurgeryCase surgeryCase(UUID patient) {
        return SurgeryCase.create(id(4), id(12), new CareEpisode(CareEpisodeType.ADMISSION, id(3), id(3), null),
                patient, id(8), id(9), "TEST_PROC", "Test-only indication", SurgeryPriority.ROUTINE,
                at.minusSeconds(120), SurgeryAuditActor.human(id(9), id(9)), "test");
    }
    private static UUID id(int suffix) { return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(suffix)); }
    private static byte[] fixture() throws Exception {
        return Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/clearance-surgery.json"));
    }
}
