package com.mediflow.clinical.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.clinical.messaging.consumer.payload.LabResultCreatedPayload;

/**
 * Pins the live lab.result.created acceptance rules before MediFlow v2.0 adds report revisions.
 * Wave 6 changes these expectations deliberately, together with the amendment consumer.
 */
class LabResultCreatedConsumerContractFixtureTest {

    @Test
    void acceptsResultVersionOneForOutpatientVisit() {
        assertThatCode(() -> LabResultCreatedConsumer.validate(payload(1, 1, "OUTPATIENT_VISIT")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsResultVersionTwoUntilAmendmentConsumerShips() {
        assertThatThrownBy(() -> LabResultCreatedConsumer.validate(payload(1, 2, "OUTPATIENT_VISIT")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lab.result.created resultVersion must be 1");
    }

    @Test
    void rejectsNonOutpatientCareEpisodeType() {
        assertThatThrownBy(() -> LabResultCreatedConsumer.validate(payload(1, 1, "TREATMENT_COURSE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lab.result.created careEpisodeType is unsupported");
    }

    @Test
    void rejectsUnknownSourceVersion() {
        assertThatThrownBy(() -> LabResultCreatedConsumer.validate(payload(2, 1, "OUTPATIENT_VISIT")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lab.result.created source version is unsupported");
    }

    private static LabResultCreatedPayload payload(int sourceVersion, int resultVersion, String careEpisodeType) {
        return new LabResultCreatedPayload(
                UUID.randomUUID(), Instant.parse("2026-11-10T01:00:00Z"), "correlation-test",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "HEMATOLOGY", LocalDate.parse("2026-11-10"),
                List.of(new LabResultCreatedPayload.Result(
                        UUID.randomUUID(), "WBC", "7.2", "10^9/L", "4.0-10.0")),
                "Normal", sourceVersion, careEpisodeType, UUID.randomUUID(), resultVersion);
    }
}
