package com.mediflow.lab.application.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import com.mediflow.lab.application.dto.response.LabResultDTO;
import com.mediflow.lab.application.dto.response.LabTestDTO;
import com.mediflow.lab.domain.model.LabResult;
import com.mediflow.lab.domain.model.LabTest;

/** Maps the pure domain aggregate to response records at the application boundary. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface LabTestDtoMapper {

    LabTestDTO toDto(LabTest labTest);

    LabResultDTO toDto(LabResult result);
}
