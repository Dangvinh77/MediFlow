package com.mediflow.surgery.application.port.out;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationLookupPortTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-09-29T02:00:00Z");

    @Test
    void snapshotPreservesTypedStatesAndNormalizesOptionalMetadata() {
        UUID referenceId = UUID.randomUUID();

        for (OrganizationLookupPort.ReferenceState state : OrganizationLookupPort.ReferenceState.values()) {
            var snapshot = new OrganizationLookupPort.OrganizationLookupSnapshot(
                    OrganizationLookupPort.ReferenceKind.STAFF, referenceId, state, OBSERVED_AT,
                    " rev-7 ", " ORTHOPAEDIC_SURGEON ");

            assertThat(snapshot.state()).isEqualTo(state);
            assertThat(snapshot.sourceRevision()).isEqualTo("rev-7");
            assertThat(snapshot.jobTitleCode()).isEqualTo("ORTHOPAEDIC_SURGEON");
            assertThat(snapshot.referenceId()).isEqualTo(referenceId);
        }
    }

    @Test
    void jobTitleIsAllowedOnlyForStaffAndMetadataCannotBeBlank() {
        UUID referenceId = UUID.randomUUID();

        assertThatThrownBy(() -> new OrganizationLookupPort.OrganizationLookupSnapshot(
                OrganizationLookupPort.ReferenceKind.ROOM, referenceId,
                OrganizationLookupPort.ReferenceState.ACTIVE, OBSERVED_AT, null, "SURGEON"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrganizationLookupPort.OrganizationLookupSnapshot(
                OrganizationLookupPort.ReferenceKind.STAFF, referenceId,
                OrganizationLookupPort.ReferenceState.UNKNOWN, OBSERVED_AT, " ", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrganizationLookupPort.OrganizationLookupSnapshot(
                OrganizationLookupPort.ReferenceKind.STAFF, referenceId,
                OrganizationLookupPort.ReferenceState.ACTIVE, OBSERVED_AT, null, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void identityAndObservationAreRequired() {
        assertThatThrownBy(() -> new OrganizationLookupPort.OrganizationLookupSnapshot(
                null, UUID.randomUUID(), OrganizationLookupPort.ReferenceState.UNKNOWN, OBSERVED_AT, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrganizationLookupPort.OrganizationLookupSnapshot(
                OrganizationLookupPort.ReferenceKind.DEPARTMENT, null,
                OrganizationLookupPort.ReferenceState.NOT_FOUND, OBSERVED_AT, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrganizationLookupPort.OrganizationLookupSnapshot(
                OrganizationLookupPort.ReferenceKind.ROOM, UUID.randomUUID(),
                OrganizationLookupPort.ReferenceState.ACTIVE, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
