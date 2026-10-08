package com.mediflow.billing.application.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Billing phải đọc đúng byte của producer — không copy/đổi tên field (CONTRACT-SURGERY-BILLING-01,
 * "Files Billing should read after pull"). Đọc trực tiếp bản sao các file fixture thật của Huy tại
 * {@code src/test/resources/contracts/surgery-outcomes-v1/} (mirror của
 * {@code backend/surgery-service/src/test/resources/contracts/surgery-outcomes-v1/}), không dựng
 * JSON tay — nếu Surgery đổi tên field, test này vỡ ngay khi pull bản fixture mới.
 */
class SurgeryEventFixtureTest {

    private static final String DIR = "contracts/surgery-outcomes-v1/";
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void caseCreatedAdmissionFixture_decodesPlannedItem() throws Exception {
        SurgeryCaseCreatedEvent event = read("surgery.case.created.admission.v1.json", SurgeryCaseCreatedEvent.class);

        assertThat(event.surgeryCaseId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000001"));
        assertThat(event.careEpisodeType()).isEqualTo("ADMISSION");
        assertThat(event.careEpisodeId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000008"));
        assertThat(event.plannedItems()).hasSize(1);
        assertThat(event.plannedItems().getFirst().priceCode()).isEqualTo("PRICE");
        assertThat(event.plannedItems().getFirst().quantity()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void caseCreatedOutpatientFixture_decodes() throws Exception {
        SurgeryCaseCreatedEvent event = read("surgery.case.created.outpatient.v1.json", SurgeryCaseCreatedEvent.class);

        assertThat(event.careEpisodeType()).isEqualTo("OUTPATIENT_VISIT");
        assertThat(event.surgeryCaseId()).isNotNull();
    }

    @Test
    void completedFixture_decodesPerformedItemMatchingPlan() throws Exception {
        SurgeryCompletedEvent event = read("surgery.completed.admission.v1.json", SurgeryCompletedEvent.class);

        assertThat(event.surgeryCaseId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000001"));
        assertThat(event.resultId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000014"));
        assertThat(event.performedItems()).hasSize(1);
        assertThat(event.performedItems().getFirst().quantity()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void completedPlannedDifferenceFixture_decodesExtraLine() throws Exception {
        SurgeryCompletedEvent event = read("surgery.completed.admission.planned-difference.v1.json", SurgeryCompletedEvent.class);

        assertThat(event.performedItems()).hasSize(2);
        assertThat(event.performedItems().get(0).quantity()).isEqualByComparingTo("2");
        assertThat(event.performedItems().get(1).priceCode()).isEqualTo("EXTRA_PRICE");
        assertThat(event.performedItems().get(1).quantity()).isEqualByComparingTo("0.5");
    }

    @Test
    void completedUnknownPriceFixture_decodesUnrecognizedPriceCode() throws Exception {
        SurgeryCompletedEvent event = read("surgery.completed.admission.unknown-price.v1.json", SurgeryCompletedEvent.class);

        assertThat(event.performedItems().getFirst().priceCode()).isEqualTo("UNRECOGNIZED_PRICE");
    }

    @Test
    void cancelledFixture_decodesPreStartCancellation() throws Exception {
        SurgeryCancelledEvent event = read("surgery.cancelled.admission.v1.json", SurgeryCancelledEvent.class);

        assertThat(event.surgeryCaseId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000001"));
        assertThat(event.cancellationId()).isEqualTo(UUID.fromString("74b182de-a395-387a-b359-62491fc7ca4c"));
        assertThat(event.cancellationStage()).isEqualTo("BEFORE_START");
    }

    private <T> T read(String fileName, Class<T> type) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(DIR + fileName)) {
            assertThat(in).as("fixture " + fileName + " phải tồn tại trong test resources").isNotNull();
            return objectMapper.readValue(in, type);
        }
    }
}
