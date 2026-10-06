package com.mediflow.organization.domain.model;

import com.mediflow.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class SurgicalCapabilityTest {
    private final Instant start = Instant.parse("2026-10-06T02:00:00Z");
    private final Instant end = start.plusSeconds(3600);
    private final Department department = Department.create(UUID.randomUUID(), "Surgery", "SUR", DepartmentType.CLINICAL, "A");

    private Staff member(JobTitle title) {
        return Staff.create("Test member", department.getDepartmentId(), title, null,
                title == JobTitle.DOCTOR ? "LIC-001" : null, null, null);
    }

    @ParameterizedTest
    @EnumSource(SurgicalTeamRole.class)
    void covers_explicitCompatibleGrant_allowsOnlyWholeInterval(SurgicalTeamRole role) {
        Staff member = member(role == SurgicalTeamRole.OR_NURSE ? JobTitle.NURSE : JobTitle.DOCTOR);
        SurgicalCapability grant = grant(member, role, true);
        assertThat(grant.covers(member, department, start, end)).isTrue();
        assertThat(grant.covers(member, department, start.minusSeconds(1), end)).isFalse();
        assertThat(grant.covers(member, department, start, end.plusSeconds(1))).isFalse();
        assertThat(grant.covers(member, department, end, end)).isFalse();
    }

    @Test
    void covers_revokedInactiveTransferredOrWrongJob_doesNotAuthorize() {
        Staff doctor = member(JobTitle.DOCTOR);
        var grant = grant(doctor, SurgicalTeamRole.PRIMARY_SURGEON, true);
        assertThat(grant(doctor, SurgicalTeamRole.PRIMARY_SURGEON, false).covers(doctor, department, start, end)).isFalse();
        assertThat(grant.covers(member(JobTitle.NURSE), department, start, end)).isFalse();
        doctor.deactivate();
        assertThat(grant.covers(doctor, department, start, end)).isFalse();
        doctor.activate();
        doctor.changeDepartment(UUID.randomUUID());
        assertThat(grant.covers(doctor, department, start, end)).isFalse();
    }

    @Test
    void operatingRoom_normalizesCodeAndRejectsInvalidReferences() {
        var room = new OperatingRoom(UUID.randomUUID(), "or-01", " Theatre ", department.getDepartmentId(), true, 1, start);
        assertThat(room.roomCode()).isEqualTo("OR-01");
        assertThat(room.roomName()).isEqualTo("Theatre");
        assertThatThrownBy(() -> new OperatingRoom(UUID.randomUUID(), "bad code", "Room",
                department.getDepartmentId(), true, 1, start)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> new OperatingRoom(UUID.randomUUID(), "OR", "Room",
                department.getDepartmentId(), true, 0, start)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void capability_invalidInterval_rejected() {
        Staff doctor = member(JobTitle.DOCTOR);
        assertThatThrownBy(() -> new SurgicalCapability(doctor.getStaffId(), SurgicalTeamRole.PRIMARY_SURGEON,
                department.getDepartmentId(), true, end, start, 1, start)).isInstanceOf(BusinessRuleException.class);
    }

    private SurgicalCapability grant(Staff member, SurgicalTeamRole role, boolean active) {
        return new SurgicalCapability(member.getStaffId(), role, department.getDepartmentId(), active,
                start, end, 1, start);
    }
}
