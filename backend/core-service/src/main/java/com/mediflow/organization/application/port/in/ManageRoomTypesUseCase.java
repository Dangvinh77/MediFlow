package com.mediflow.organization.application.port.in;

import com.mediflow.organization.domain.model.DepartmentType;
import com.mediflow.organization.domain.model.RoomTypeDefinition;

import java.util.List;

/** Administration of the room type catalogue shown when creating rooms. */
public interface ManageRoomTypesUseCase {

    List<RoomTypeUsage> list();

    RoomTypeUsage create(NewRoomType command);

    RoomTypeUsage rename(String code, String name, String description);

    void delete(String code);

    record NewRoomType(String name, String description, DepartmentType departmentType,
                       boolean singlePerDepartment) {}

    record RoomTypeUsage(RoomTypeDefinition type, long roomCount) {}
}
