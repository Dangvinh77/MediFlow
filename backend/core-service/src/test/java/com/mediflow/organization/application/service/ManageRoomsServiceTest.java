package com.mediflow.organization.application.service;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.organization.application.port.in.ManageRoomsUseCase.RoomDetails;
import com.mediflow.organization.application.port.in.ManageRoomsUseCase.RoomPlacement;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.application.port.out.RoomRepository;
import com.mediflow.organization.domain.exception.InvalidRoomException;
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.DepartmentType;
import com.mediflow.organization.domain.model.Room;
import com.mediflow.organization.domain.model.RoomType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManageRoomsServiceTest {

    private final RoomRepository rooms = mock(RoomRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final ManageRoomsService service = new ManageRoomsService(rooms, departments);

    private static final RoomPlacement PLACEMENT =
            new RoomPlacement("101", "Phòng khám Nội 1", "Khu A", "Tầng 1, Khu A");

    @Test
    void clinicalDepartmentOwnsManyConsultationRooms() {
        Department clinical = department(DepartmentType.CLINICAL);
        when(rooms.findActiveByDepartment(eq(clinical.getDepartmentId()), any()))
                .thenReturn(List.of(room(clinical, RoomType.CONSULTATION), room(clinical, RoomType.DEPARTMENT_OFFICE)));
        when(rooms.save(any())).thenAnswer(call -> call.getArgument(0));

        Room created = service.create(new RoomDetails(clinical.getDepartmentId(), RoomType.CONSULTATION, PLACEMENT));

        assertThat(created.getRoomType()).isEqualTo(RoomType.CONSULTATION);
        assertThat(created.getRoomNumber()).isEqualTo("101");
        assertThat(created.getZone()).isEqualTo("Khu A");
        assertThat(created.isActive()).isTrue();
    }

    @Test
    void clinicalDepartmentKeepsOneSharedOffice() {
        Department clinical = department(DepartmentType.CLINICAL);
        when(rooms.findActiveByDepartment(eq(clinical.getDepartmentId()), any()))
                .thenReturn(List.of(room(clinical, RoomType.DEPARTMENT_OFFICE)));

        assertThatThrownBy(() -> service.create(
                new RoomDetails(clinical.getDepartmentId(), RoomType.DEPARTMENT_OFFICE, PLACEMENT)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("ROOM_TYPE_ALREADY_ASSIGNED");
        verify(rooms, never()).save(any());
    }

    @Test
    void functionalUnitWorksInExactlyOneRoom() {
        Department cashier = department(DepartmentType.ADMINISTRATIVE);
        when(rooms.findActiveByDepartment(eq(cashier.getDepartmentId()), any()))
                .thenReturn(List.of(room(cashier, RoomType.CASHIER)));

        assertThatThrownBy(() -> service.create(
                new RoomDetails(cashier.getDepartmentId(), RoomType.ADMINISTRATION, PLACEMENT)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("ROOM_SINGLE_WORKPLACE");
    }

    @ParameterizedTest
    @EnumSource(RoomType.class)
    void roomTypeMustFitTheDepartmentType(RoomType type) {
        for (DepartmentType departmentType : DepartmentType.values()) {
            Department department = department(departmentType);
            when(rooms.findActiveByDepartment(eq(department.getDepartmentId()), any())).thenReturn(List.of());
            when(rooms.save(any())).thenAnswer(call -> call.getArgument(0));
            RoomDetails details = new RoomDetails(department.getDepartmentId(), type, PLACEMENT);
            if (type.allowedFor(departmentType)) {
                assertThat(service.create(details).getRoomType()).isEqualTo(type);
            } else {
                assertThatThrownBy(() -> service.create(details))
                        .isInstanceOf(BusinessRuleException.class)
                        .extracting("code").isEqualTo("ROOM_TYPE_NOT_ALLOWED");
            }
        }
    }

    @Test
    void consultationRoomsBelongToClinicalDepartmentsOnly() {
        assertThat(RoomType.CONSULTATION.allowedFor(DepartmentType.CLINICAL)).isTrue();
        assertThat(RoomType.CONSULTATION.allowedFor(DepartmentType.PARACLINICAL)).isFalse();
        assertThat(RoomType.CONSULTATION.allowedFor(DepartmentType.ADMINISTRATIVE)).isFalse();
        assertThat(RoomType.LABORATORY.allowedFor(DepartmentType.PARACLINICAL)).isTrue();
        assertThat(RoomType.NURSING.allowedFor(DepartmentType.ADMINISTRATIVE)).isTrue();
    }

    @Test
    void roomNumberIsUniqueWithinItsZone() {
        Department clinical = department(DepartmentType.CLINICAL);
        when(rooms.findActiveByDepartment(any(), any())).thenReturn(List.of());
        when(rooms.existsNumberInZone(eq("Khu A"), eq("101"), any())).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new RoomDetails(clinical.getDepartmentId(), RoomType.CONSULTATION, PLACEMENT)))
                .isInstanceOf(DuplicateResourceException.class)
                .extracting("code").isEqualTo("ROOM_NUMBER_DUPLICATE");
    }

    @Test
    void inactiveDepartmentReceivesNoRoom() {
        Department clinical = department(DepartmentType.CLINICAL);
        clinical.deactivate(false);

        assertThatThrownBy(() -> service.create(
                new RoomDetails(clinical.getDepartmentId(), RoomType.CONSULTATION, PLACEMENT)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("ROOM_DEPARTMENT_INACTIVE");
    }

    @Test
    void placementFieldsAreRequired() {
        Department clinical = department(DepartmentType.CLINICAL);
        when(rooms.findActiveByDepartment(any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(new RoomDetails(clinical.getDepartmentId(),
                RoomType.CONSULTATION, new RoomPlacement(" ", "Phòng khám", "Khu A", "Tầng 1"))))
                .isInstanceOf(InvalidRoomException.class);
        assertThatThrownBy(() -> service.create(new RoomDetails(clinical.getDepartmentId(),
                RoomType.CONSULTATION, new RoomPlacement("101", "Phòng khám", "", "Tầng 1"))))
                .isInstanceOf(InvalidRoomException.class);
    }

    @Test
    void updateChangesPlacementButKeepsDepartmentAndType() {
        Department clinical = department(DepartmentType.CLINICAL);
        Room room = room(clinical, RoomType.CONSULTATION);
        when(rooms.lockById(room.getRoomId())).thenReturn(Optional.of(room));
        when(rooms.save(any())).thenAnswer(call -> call.getArgument(0));

        Room updated = service.update(room.getRoomId(), new RoomPlacement("205", "Phòng khám Nội 3", "Khu B", "Tầng 2"));

        assertThat(updated.getRoomNumber()).isEqualTo("205");
        assertThat(updated.getZone()).isEqualTo("Khu B");
        assertThat(updated.getDepartmentId()).isEqualTo(clinical.getDepartmentId());
        assertThat(updated.getRoomType()).isEqualTo(RoomType.CONSULTATION);
    }

    @Test
    void reactivationRechecksTheSingleWorkplaceRule() {
        Department lab = department(DepartmentType.PARACLINICAL);
        Room old = room(lab, RoomType.LABORATORY);
        old.deactivate();
        when(rooms.findById(old.getRoomId())).thenReturn(Optional.of(old));
        when(rooms.lockById(old.getRoomId())).thenReturn(Optional.of(old));
        when(rooms.findActiveByDepartment(lab.getDepartmentId(), old.getRoomId()))
                .thenReturn(List.of(room(lab, RoomType.LABORATORY)));

        assertThatThrownBy(() -> service.changeStatus(old.getRoomId(), true))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("ROOM_SINGLE_WORKPLACE");

        when(rooms.findActiveByDepartment(lab.getDepartmentId(), old.getRoomId())).thenReturn(List.of());
        when(rooms.save(any())).thenAnswer(call -> call.getArgument(0));
        assertThat(service.changeStatus(old.getRoomId(), true).isActive()).isTrue();
    }

    @Test
    void missingRoomIsNotFound() {
        UUID id = UUID.randomUUID();
        when(rooms.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.changeStatus(id, false)).isInstanceOf(ResourceNotFoundException.class);
    }

    private Department department(DepartmentType type) {
        Department department = Department.create(UUID.randomUUID(), "Khoa " + type, "K" + type.ordinal(), type, "Tầng 1");
        when(departments.findByIdForUpdate(department.getDepartmentId())).thenReturn(Optional.of(department));
        return department;
    }

    private static Room room(Department department, RoomType type) {
        return Room.create(UUID.randomUUID(), department.getDepartmentId(), type,
                "R" + UUID.randomUUID().toString().substring(0, 4), "Phòng", "Khu A", "Tầng 1");
    }
}
