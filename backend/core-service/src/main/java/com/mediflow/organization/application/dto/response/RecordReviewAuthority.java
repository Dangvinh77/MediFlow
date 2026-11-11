package com.mediflow.organization.application.dto.response;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecordReviewAuthority(UUID actorAccountId, UUID staffId, Boolean canReviewRecords) {}
