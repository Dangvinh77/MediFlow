package com.mediflow.patient.application.mapper;

import com.mediflow.patient.application.dto.response.PatientDTO;
import com.mediflow.patient.domain.model.Patient;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * Domain model → response DTO. One direction only.
 *
 * <p>There is no {@code request → Patient} mapping here on purpose: a new aggregate is built by
 * {@code Patient.taoMoi(...)} so the invariants always run. A mapper that populated fields
 * directly would bypass them.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PatientDtoMapper {

    PatientDTO toDto(Patient patient);
}
