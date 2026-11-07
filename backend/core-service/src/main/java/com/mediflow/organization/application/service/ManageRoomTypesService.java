package com.mediflow.organization.application.service;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.organization.application.port.in.ManageRoomTypesUseCase;
import com.mediflow.organization.application.port.out.RoomTypeRepository;
import com.mediflow.organization.domain.exception.InvalidRoomException;
import com.mediflow.organization.domain.model.RoomTypeDefinition;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Room type catalogue rules: names are unique, the department category and room-count rule
 * of a type never change, system-defined types cannot be deleted, and a type that any room
 * (active or historical) uses cannot be deleted.
 */
@Transactional("organizationTransactionManager")
public class ManageRoomTypesService implements ManageRoomTypesUseCase {

    private final RoomTypeRepository types;

    public ManageRoomTypesService(RoomTypeRepository types) {
        this.types = types;
    }

    @Override
    @Transactional(value = "organizationTransactionManager", readOnly = true)
    public List<RoomTypeUsage> list() {
        Map<String, Long> counts = types.roomCounts();
        return types.findAll().stream()
                .sorted(Comparator.comparing(RoomTypeDefinition::getDepartmentType)
                        .thenComparingInt(RoomTypeDefinition::getSortOrder)
                        .thenComparing(RoomTypeDefinition::getName))
                .map(type -> new RoomTypeUsage(type, counts.getOrDefault(type.getCode(), 0L)))
                .toList();
    }

    @Override
    public RoomTypeUsage create(NewRoomType command) {
        if (command == null || command.departmentType() == null) {
            throw new InvalidRoomException("Room type name and department category are required");
        }
        RoomTypeDefinition type = RoomTypeDefinition.define(command.name(), command.description(),
                command.departmentType(), command.singlePerDepartment(), types.nextSortOrder());
        if (types.existsByName(type.getName(), null) || types.findByCode(type.getCode()).isPresent()) {
            throw duplicate();
        }
        types.insert(type);
        return new RoomTypeUsage(type, 0);
    }

    @Override
    public RoomTypeUsage rename(String code, String name, String description) {
        RoomTypeDefinition type = types.lockByCode(code).orElseThrow(ManageRoomTypesService::notFound);
        type.rename(name, description);
        if (types.existsByName(type.getName(), type.getCode())) throw duplicate();
        types.update(type);
        return new RoomTypeUsage(type, types.roomCounts().getOrDefault(type.getCode(), 0L));
    }

    @Override
    public void delete(String code) {
        RoomTypeDefinition type = types.lockByCode(code).orElseThrow(ManageRoomTypesService::notFound);
        if (type.isSystemDefined()) {
            throw new BusinessRuleException("ROOM_TYPE_SYSTEM",
                    "System room types cannot be deleted");
        }
        if (types.roomCounts().getOrDefault(type.getCode(), 0L) > 0) {
            throw new BusinessRuleException("ROOM_TYPE_IN_USE",
                    "Room type is used by existing rooms");
        }
        types.delete(type.getCode());
    }

    private static DuplicateResourceException duplicate() {
        return new DuplicateResourceException("ROOM_TYPE_DUPLICATE", "Room type already exists");
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("ROOM_TYPE_NOT_FOUND", "Room type not found");
    }
}
