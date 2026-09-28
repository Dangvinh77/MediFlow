package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.domain.exception.SurgeryRuleException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CareEpisodeTest {

    @Test
    void outpatientEpisode_appointmentCanBeSelectedWhileRecordRemainsContext() {
        UUID appointmentId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();

        CareEpisode episode = new CareEpisode(
                CareEpisodeType.OUTPATIENT_VISIT, appointmentId, null, recordId);

        assertThat(episode.episodeId()).isEqualTo(appointmentId);
        assertThat(episode.recordId()).isEqualTo(recordId);
    }

    @Test
    void admissionEpisode_mustUseExactAdmissionId() {
        UUID admissionId = UUID.randomUUID();

        CareEpisode episode = new CareEpisode(
                CareEpisodeType.ADMISSION, admissionId, admissionId, UUID.randomUUID());

        assertThat(episode.type()).isEqualTo(CareEpisodeType.ADMISSION);
        assertThat(episode.episodeId()).isEqualTo(admissionId);
    }

    @Test
    void admissionEpisode_mismatchedId_isRejected() {
        assertThatThrownBy(() -> new CareEpisode(
                CareEpisodeType.ADMISSION,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null))
                .isInstanceOf(SurgeryRuleException.class)
                .hasMessageContaining("trùng chính xác");
    }

    @Test
    void outpatientEpisode_cannotUseAdmissionId() {
        assertThatThrownBy(() -> new CareEpisode(
                CareEpisodeType.OUTPATIENT_VISIT,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()))
                .isInstanceOf(SurgeryRuleException.class)
                .hasMessageContaining("Đợt ngoại trú");
    }
}
