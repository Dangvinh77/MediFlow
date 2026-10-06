package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.domain.model.SurgeryAuthorityChange.ReferenceKind;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SurgeryAuthorityChangeTest {
    private static final Instant NOW = Instant.parse("2026-10-05T02:01:00Z");
    @Test void exactRoomOrStaffRoleIsRequiredToAffectSchedule() {
        UUID room = UUID.randomUUID(), staff = UUID.randomUUID();
        var schedule = new SurgerySchedule(UUID.randomUUID(), UUID.randomUUID(), 1, room, NOW, NOW.plusSeconds(60),
                List.of(new SurgeryTeamAssignment(staff, SurgeryTeamRole.PRIMARY_SURGEON)));
        assertThat(change(ReferenceKind.ROOM, room, null).affects(schedule)).isTrue();
        assertThat(change(ReferenceKind.ROOM, UUID.randomUUID(), null).affects(schedule)).isFalse();
        assertThat(change(ReferenceKind.STAFF_CAPABILITY, staff, SurgeryTeamRole.PRIMARY_SURGEON).affects(schedule)).isTrue();
        assertThat(change(ReferenceKind.STAFF_CAPABILITY, staff, SurgeryTeamRole.ASSISTANT_SURGEON).affects(schedule)).isFalse();
        assertThat(change(ReferenceKind.STAFF_CAPABILITY, UUID.randomUUID(), SurgeryTeamRole.PRIMARY_SURGEON).affects(schedule)).isFalse();
    }
    @Test void referenceKeysSeparateRoomStaffAndDifferentCapabilities() {
        UUID id = UUID.randomUUID();
        assertThat(change(ReferenceKind.ROOM, id, null).referenceKey()).isEqualTo("ROOM:" + id);
        assertThat(change(ReferenceKind.STAFF_CAPABILITY, id, SurgeryTeamRole.PRIMARY_SURGEON).referenceKey())
                .isEqualTo("STAFF_CAPABILITY:" + id + ":PRIMARY_SURGEON");
        assertThat(change(ReferenceKind.STAFF_CAPABILITY, id, SurgeryTeamRole.ASSISTANT_SURGEON).referenceKey())
                .isNotEqualTo(change(ReferenceKind.STAFF_CAPABILITY, id, SurgeryTeamRole.PRIMARY_SURGEON).referenceKey());
    }
    @Test void referenceKindsCannotForgeMissingOrUnrelatedRole() {
        assertThatThrownBy(() -> change(ReferenceKind.ROOM, UUID.randomUUID(), SurgeryTeamRole.PRIMARY_SURGEON)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> change(ReferenceKind.STAFF_CAPABILITY, UUID.randomUUID(), null)).isInstanceOf(IllegalArgumentException.class);
    }
    private SurgeryAuthorityChange change(ReferenceKind kind, UUID reference, SurgeryTeamRole role) {
        return new SurgeryAuthorityChange(kind, reference, role, 1, NOW, UUID.randomUUID(), "Test decision", "a".repeat(64));
    }
}
