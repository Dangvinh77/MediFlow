package com.mediflow.organization.web.dto.response;

import com.mediflow.organization.application.port.in.ManageRoomTypesUseCase.RoomTypeUsage;
import com.mediflow.organization.domain.model.DepartmentType;
import com.mediflow.organization.domain.model.RoomTypeDefinition;

import java.time.Instant;

public record RoomTypeResponse(
        String code,
        String name,
        String description,
        DepartmentType departmentType,
        boolean singlePerDepartment,
        boolean schedulable,
        boolean systemDefined,
        long roomCount,
        Instant createdAt,
        Instant updatedAt) {

    public static RoomTypeResponse from(RoomTypeUsage usage) {
        RoomTypeDefinition type = usage.type();
        return new RoomTypeResponse(type.getCode(), type.getName(), type.getDescription(),
                type.getDepartmentType(), type.isSinglePerDepartment(), type.schedulable(),
                type.isSystemDefined(), usage.roomCount(), type.getCreatedAt(), type.getUpdatedAt());
    }
}
