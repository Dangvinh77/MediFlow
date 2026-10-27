package com.mediflow.clinical.infrastructure.messaging;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.event.DomainEventEnvelope;
import com.mediflow.clinical.application.event.FollowUpRecommendationCreatedFact;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class FollowUpRecommendationContractFixtureTest {
    @Test void actualSerializerMatchesReleasedNoticeFixture() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var payload = new FollowUpRecommendationCreatedFact(id(22), id(23), id(24), id(25), id(26), id(27),
                LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-07"));
        var event = new DomainEventEnvelope<>(id(21), "followup.recommendation.created", 1,
                Instant.parse("2026-10-27T01:00:00Z"), "portal-contract", "clinical-service", payload);
        try (var input = getClass().getResourceAsStream("/contracts/followup.recommendation.created.v1.json")) {
            assertThat(input).isNotNull();
            assertThat(mapper.readTree(new FollowUpRecommendationEventSerializer(mapper).serialize(event)))
                .isEqualTo(mapper.readTree(input));
        }
    }
    private static UUID id(int n) { return UUID.fromString("00000000-0000-4000-8000-%012d".formatted(n)); }
}
