package com.mediflow.organization.application.mapper;

import com.mediflow.organization.application.dto.response.OperatingRoomDTO;
import com.mediflow.organization.application.dto.response.SurgicalCapabilityDTO;
import com.mediflow.organization.domain.model.OperatingRoom;
import com.mediflow.organization.domain.model.SurgicalCapability;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/** Domain records stay internal; public command responses use the module's DTO mapping layer. */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SurgeryAuthorityMapper {
    OperatingRoomDTO toDto(OperatingRoom room);
    SurgicalCapabilityDTO toDto(SurgicalCapability capability);
}
