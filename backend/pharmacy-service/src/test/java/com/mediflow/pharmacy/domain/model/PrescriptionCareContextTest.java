package com.mediflow.pharmacy.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;
import com.mediflow.pharmacy.domain.model.enums.CareContext;
import com.mediflow.pharmacy.domain.model.enums.CareContractVersion;
import com.mediflow.pharmacy.domain.model.enums.CareEpisodeType;

class PrescriptionCareContextTest {

    private static final UUID OUTPATIENT_EPISODE =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMISSION_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void legacy_preservesVersionZeroWithoutInventedEpisode() {
        PrescriptionCareContext context = PrescriptionCareContext.legacy();

        assertThat(context.contractVersion()).isEqualTo(CareContractVersion.LEGACY);
        assertThat(context.careContext()).isEqualTo(CareContext.OUTPATIENT);
        assertThat(context.episode()).isNull();
        assertThat(context.admissionId()).isNull();
        assertThat(context.priceCode()).isNull();
    }

    @Test
    void versionOneOutpatient_requiresExactVisitAndNoAdmissionId() {
        PrescriptionCareContext context = PrescriptionCareContext.v1(
                CareContext.OUTPATIENT,
                new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, OUTPATIENT_EPISODE),
                null,
                "RX-001");

        assertThat(context.episode().id()).isEqualTo(OUTPATIENT_EPISODE);
        assertThat(context.admissionId()).isNull();
    }

    @Test
    void versionOneOutpatient_withAdmissionId_rejects() {
        assertThatThrownBy(() -> PrescriptionCareContext.v1(
                CareContext.OUTPATIENT,
                new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, OUTPATIENT_EPISODE),
                ADMISSION_ID,
                "RX-001"))
                .isInstanceOf(PrescriptionRuleException.class)
                .hasMessageContaining("Outpatient");
    }

    @Test
    void versionOneAdmission_requiresMatchingAdmissionEpisode() {
        PrescriptionCareContext context = PrescriptionCareContext.v1(
                CareContext.ADMISSION,
                new CareEpisode(CareEpisodeType.ADMISSION, ADMISSION_ID),
                ADMISSION_ID,
                "RX-001");

        assertThat(context.episode().id()).isEqualTo(context.admissionId());
    }

    @Test
    void versionOneAdmission_withMismatchedAdmissionId_rejects() {
        assertThatThrownBy(() -> PrescriptionCareContext.v1(
                CareContext.ADMISSION,
                new CareEpisode(CareEpisodeType.ADMISSION, ADMISSION_ID),
                OUTPATIENT_EPISODE,
                "RX-001"))
                .isInstanceOf(PrescriptionRuleException.class)
                .hasMessageContaining("must equal");
    }

    @Test
    void versionOneOutpatient_withAdmissionEpisode_rejects() {
        assertThatThrownBy(() -> PrescriptionCareContext.v1(
                CareContext.OUTPATIENT,
                new CareEpisode(CareEpisodeType.ADMISSION, ADMISSION_ID),
                null,
                "RX-001"))
                .isInstanceOf(PrescriptionRuleException.class);
    }

    @Test
    void versionOne_missingEpisodeOrPriceCode_rejects() {
        assertThatThrownBy(() -> PrescriptionCareContext.v1(
                CareContext.OUTPATIENT, null, null, "RX-001"))
                .isInstanceOf(PrescriptionRuleException.class);
        assertThatThrownBy(() -> PrescriptionCareContext.v1(
                CareContext.OUTPATIENT,
                new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, OUTPATIENT_EPISODE),
                null,
                " "))
                .isInstanceOf(PrescriptionRuleException.class);
    }

    @Test
    void versionZero_withVersionOneFields_rejects() {
        assertThatThrownBy(() -> new PrescriptionCareContext(
                CareContractVersion.LEGACY,
                CareContext.OUTPATIENT,
                new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, OUTPATIENT_EPISODE),
                null,
                null))
                .isInstanceOf(PrescriptionRuleException.class);
    }

    @Test
    void contractVersion_unknownValue_rejectsInsteadOfDowngrading() {
        assertThatThrownBy(() -> CareContractVersion.from(2))
                .isInstanceOf(PrescriptionRuleException.class)
                .hasMessageContaining("Unsupported");
    }
}
