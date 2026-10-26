package com.mediflow.organization.application.service;
import com.mediflow.organization.application.port.out.*;
import com.mediflow.organization.domain.model.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetCareLabelServiceTest {
    private final StaffRepository staff = mock(StaffRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final GetCareLabelService labels = new GetCareLabelService(staff, departments, rooms);
    @Test void exposesDoctorsOnlyAndKeepsHistoricalNames() {
        var doctor = Staff.create("Nguyễn An", UUID.randomUUID(), JobTitle.DOCTOR, "Nội", "LICENSE", null, null, "012345678901");
        when(staff.findById(doctor.getStaffId())).thenReturn(Optional.of(doctor));
        assertThat(labels.read("doctor", doctor.getStaffId()).orElseThrow().label()).isEqualTo("Nguyễn An");
        var nurse = Staff.create("Private nurse", UUID.randomUUID(), JobTitle.NURSE, null, null, null, null, "012345678902");
        when(staff.findById(nurse.getStaffId())).thenReturn(Optional.of(nurse));
        assertThat(labels.read("doctor", nurse.getStaffId())).isEmpty();
    }
    @Test void exposesOnlyConsultationRoomNamesAndRejectsUnknownKindsBeforeStorage() {
        var id = UUID.randomUUID();
        when(rooms.findById(id)).thenReturn(Optional.of(Room.create(id, UUID.randomUUID(), "Phòng Nội 1", "CONSULTATION")));
        assertThat(labels.read("room", id).orElseThrow().label()).isEqualTo("Phòng Nội 1");
        when(rooms.findById(id)).thenReturn(Optional.of(Room.create(id, UUID.randomUUID(), "Operating room", "OPERATING")));
        assertThat(labels.read("room", id)).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> labels.read("account", id));
        verifyNoInteractions(staff, departments);
    }
    @Test void missingReferencesNeverManufactureNames() {
        assertThat(labels.read("doctor", UUID.randomUUID())).isEmpty();
        assertThat(labels.read("department", UUID.randomUUID())).isEmpty();
        assertThat(labels.read("room", UUID.randomUUID())).isEmpty();
    }
}
