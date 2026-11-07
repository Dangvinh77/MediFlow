package com.mediflow.organization.application.service;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.organization.application.port.in.ManageRoomsUseCase;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.application.port.out.RoomRepository;
import com.mediflow.organization.domain.exception.DepartmentNotFoundException;
import com.mediflow.organization.domain.exception.InvalidRoomException;
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.Room;
import com.mediflow.organization.domain.model.RoomType;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Workplace room rules: a room type must fit its department type, a functional unit works in
 * exactly one active room, a clinical department keeps one shared office, and a room number
 * is unique within its zone. Department and type never change after creation.
 */
@Transactional("organizationTransactionManager")
public class ManageRoomsService implements ManageRoomsUseCase {

    private final RoomRepository rooms;
    private final DepartmentRepository departments;

    public ManageRoomsService(RoomRepository rooms, DepartmentRepository departments) {
        this.rooms = rooms;
        this.departments = departments;
    }

    @Override
    @Transactional(value = "organizationTransactionManager", readOnly = true)
    public List<Room> list(UUID departmentId, RoomType roomType, boolean activeOnly) {
        return rooms.search(departmentId, roomType, activeOnly);
    }

    @Override
    @Transactional(value = "organizationTransactionManager", readOnly = true)
    public Room get(UUID roomId) {
        return rooms.findById(roomId).orElseThrow(ManageRoomsService::roomNotFound);
    }

    @Override
    public Room create(RoomDetails details) {
        if (details == null || details.departmentId() == null || details.roomType() == null
                || details.placement() == null) {
            throw new InvalidRoomException("Department, room type and placement are required");
        }
        // Lock the department first: it serializes the single-workplace rules per unit.
        Department department = departments.findByIdForUpdate(details.departmentId())
                .orElseThrow(() -> new DepartmentNotFoundException(details.departmentId()));
        requireActiveDepartment(department);
        if (!details.roomType().allowedFor(department.getDepartmentType())) {
            throw new BusinessRuleException("ROOM_TYPE_NOT_ALLOWED",
                    "Room type " + details.roomType() + " does not fit a "
                            + department.getDepartmentType() + " department");
        }
        Room room = Room.create(UUID.randomUUID(), department.getDepartmentId(), details.roomType(),
                details.placement().roomNumber(), details.placement().roomName(),
                details.placement().zone(), details.placement().location());
        requireWorkplaceSlot(room);
        requireUniqueNumber(room);
        return rooms.save(room);
    }

    @Override
    public Room update(UUID roomId, RoomPlacement placement) {
        if (placement == null) throw new InvalidRoomException("Room placement is required");
        Room room = rooms.lockById(roomId).orElseThrow(ManageRoomsService::roomNotFound);
        room.update(placement.roomNumber(), placement.roomName(), placement.zone(),
                placement.location());
        requireUniqueNumber(room);
        return rooms.save(room);
    }

    @Override
    public Room changeStatus(UUID roomId, boolean active) {
        Room current = rooms.findById(roomId).orElseThrow(ManageRoomsService::roomNotFound);
        Department department = departments.findByIdForUpdate(current.getDepartmentId())
                .orElseThrow(() -> new DepartmentNotFoundException(current.getDepartmentId()));
        Room room = rooms.lockById(roomId).orElseThrow(ManageRoomsService::roomNotFound);
        if (room.isActive() == active) return room;
        if (active) {
            requireActiveDepartment(department);
            requireWorkplaceSlot(room);
            room.activate();
        } else {
            room.deactivate();
        }
        return rooms.save(room);
    }

    private void requireWorkplaceSlot(Room room) {
        List<Room> others = rooms.findActiveByDepartment(room.getDepartmentId(), room.getRoomId());
        if (room.getRoomType().functionalWorkplace() && !others.isEmpty()) {
            throw new BusinessRuleException("ROOM_SINGLE_WORKPLACE",
                    "A functional unit works in exactly one active room");
        }
        if (room.getRoomType().singlePerDepartment()
                && others.stream().anyMatch(other -> other.getRoomType() == room.getRoomType())) {
            throw new BusinessRuleException("ROOM_TYPE_ALREADY_ASSIGNED",
                    "The department already has an active room of type " + room.getRoomType());
        }
    }

    private void requireUniqueNumber(Room room) {
        if (rooms.existsNumberInZone(room.getZone(), room.getRoomNumber(), room.getRoomId())) {
            throw new DuplicateResourceException("ROOM_NUMBER_DUPLICATE",
                    "Room number already exists in this zone");
        }
    }

    private static void requireActiveDepartment(Department department) {
        if (!department.isActive()) {
            throw new BusinessRuleException("ROOM_DEPARTMENT_INACTIVE",
                    "Rooms can only be assigned to an active department");
        }
    }

    private static ResourceNotFoundException roomNotFound() {
        return new ResourceNotFoundException("ROOM_NOT_FOUND", "Room not found");
    }
}
