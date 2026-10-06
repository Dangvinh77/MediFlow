package com.mediflow.surgery.application.mapper;

import com.mediflow.surgery.application.dto.response.SurgeryCaseDetails;
import com.mediflow.surgery.domain.model.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SurgeryReadMapper {
    @Mapping(target = "episodeType", source = "careEpisode.episodeType")
    @Mapping(target = "episodeId", source = "careEpisode.episodeId")
    @Mapping(target = "admissionId", source = "careEpisode.admissionId")
    @Mapping(target = "medicalRecordId", source = "careEpisode.medicalRecordId")
    SurgeryCaseDetails.Core core(SurgeryCase value);
    SurgeryCaseDetails.Item item(SurgeryChecklistItem value);
    SurgeryCaseDetails.TeamMember member(SurgeryTeamAssignment value);
    @Mapping(target = "team", source = "teamAssignments")
    SurgeryCaseDetails.Schedule schedule(SurgerySchedule value);
    SurgeryCaseDetails.Result result(SurgeryResult value);
    SurgeryCaseDetails.PerformedItem performedItem(SurgeryPerformedItem value);
    SurgeryCaseDetails.Transition transition(SurgeryStateChange value);
}
