package com.mediflow.lab.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.lab.domain.exception.LabRuleException;

class LabSpecimenTest {
    static final Instant AT = Instant.parse("2026-11-11T04:00:00Z");
    final UUID patient = UUID.randomUUID();
    final UUID nurse = UUID.randomUUID();
    final UUID tech = UUID.randomUUID();

    LabSpecimen collected() {
        return LabSpecimen.collect(patient, "BLOOD", List.of(UUID.randomUUID()), null, nurse, AT);
    }

    @Test void rejectionNeedsAReason() {
        LabSpecimen specimen = collected();
        assertThatThrownBy(() -> specimen.decide(LabSpecimenDecision.REJECTED, " ", tech, AT))
                .isInstanceOfSatisfying(LabRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("LAB_SPECIMEN_REJECTION_REASON_REQUIRED"));
        assertThat(specimen.getDecision()).isNull();
    }

    @Test void decisionIsTakenOnce() {
        LabSpecimen specimen = collected();
        specimen.decide(LabSpecimenDecision.ACCEPTED, null, tech, AT);
        assertThat(specimen.getReceivedBy()).isEqualTo(tech);
        assertThatThrownBy(() -> specimen.decide(LabSpecimenDecision.REJECTED, "Tan huyết", tech, AT))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test void accessionIsGeneratedAndTestsAreRequired() {
        assertThat(collected().getAccession()).matches("SP-\\d{6}-[0-9A-F]{8}");
        assertThatThrownBy(() -> LabSpecimen.collect(patient, "BLOOD", List.of(), null, nurse, AT))
                .isInstanceOf(LabRuleException.class);
        assertThatThrownBy(() -> LabSpecimen.collect(patient, " ", List.of(UUID.randomUUID()), null, nurse, AT))
                .isInstanceOf(LabRuleException.class);
    }
}
