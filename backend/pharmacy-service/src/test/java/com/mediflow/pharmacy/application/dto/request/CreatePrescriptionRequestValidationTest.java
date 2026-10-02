package com.mediflow.pharmacy.application.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.pharmacy.domain.model.enums.CareContext;
import com.mediflow.pharmacy.domain.model.enums.CareEpisodeType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CreatePrescriptionRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void legacyRequest_withoutV2Fields_remainsValid() {
        CreatePrescriptionRequest request = request(
                UUID.randomUUID(), null, null, null, null, null, null);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void v1OutpatientRequest_withExactEpisode_isValidWithoutRecordId() {
        CreatePrescriptionRequest request = request(
                null, 1, CareContext.OUTPATIENT, CareEpisodeType.OUTPATIENT_VISIT,
                UUID.randomUUID(), null, "MEDICATION-PRICE-01");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void selectedV1Request_missingEpisode_rejectsInsteadOfFallingBackToLegacy() {
        CreatePrescriptionRequest request = request(
                UUID.randomUUID(), 1, CareContext.OUTPATIENT, null,
                null, null, "MEDICATION-PRICE-01");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("careContractValid");
    }

    @Test
    void v1AdmissionRequest_withMismatchedAdmissionAndEpisode_rejects() {
        CreatePrescriptionRequest request = request(
                null, 1, CareContext.ADMISSION, CareEpisodeType.ADMISSION,
                UUID.randomUUID(), UUID.randomUUID(), "MEDICATION-PRICE-01");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("careContractValid");
    }

    @Test
    void v1MetadataWithoutExplicitVersion_rejectsInsteadOfFallingBackToLegacy() {
        CreatePrescriptionRequest request = request(
                UUID.randomUUID(), null, CareContext.OUTPATIENT, CareEpisodeType.OUTPATIENT_VISIT,
                UUID.randomUUID(), null, "MEDICATION-PRICE-01");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("careContractValid");
    }

    private CreatePrescriptionRequest request(
            UUID recordId,
            Integer version,
            CareContext careContext,
            CareEpisodeType episodeType,
            UUID episodeId,
            UUID admissionId,
            String priceCode) {
        return new CreatePrescriptionRequest(
                recordId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now(),
                List.of(new PrescriptionLineRequest(UUID.randomUUID(), 1, "Once daily")),
                version,
                careContext,
                episodeType,
                episodeId,
                admissionId,
                priceCode);
    }
}
