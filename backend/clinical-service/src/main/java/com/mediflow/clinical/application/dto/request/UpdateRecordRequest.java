package com.mediflow.clinical.application.dto.request;

import com.mediflow.clinical.domain.model.ClinicalContent;

import jakarta.validation.constraints.Size;

/** A null section keeps what is stored; an empty string clears it. */
public record UpdateRecordRequest(
        @Size(max = 4000) String symptoms,
        @Size(max = 4000) String chiefComplaint,
        @Size(max = 4000) String presentIllness,
        @Size(max = 4000) String pastHistory,
        @Size(max = 4000) String allergyNote,
        @Size(max = 4000) String physicalExam,
        @Size(max = 4000) String advice
) {
    public UpdateRecordRequest(String symptoms) {
        this(symptoms, null, null, null, null, null, null);
    }

    public ClinicalContent content() {
        return new ClinicalContent(chiefComplaint, presentIllness, pastHistory, allergyNote, physicalExam, advice);
    }
}
