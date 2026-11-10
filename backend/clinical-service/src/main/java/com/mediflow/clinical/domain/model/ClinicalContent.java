package com.mediflow.clinical.domain.model;

import java.util.stream.Stream;

import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;

/**
 * The structured sections a doctor writes during one visit. {@code allergyNote} is what was said at this visit;
 * the patient-level allergy list is a separate record.
 */
public record ClinicalContent(String chiefComplaint, String presentIllness, String pastHistory, String allergyNote,
        String physicalExam, String advice) {
    public static final int MAX_SECTION_LENGTH = 4000;
    public static final ClinicalContent EMPTY = new ClinicalContent(null, null, null, null, null, null);

    public ClinicalContent {
        if (Stream.of(chiefComplaint, presentIllness, pastHistory, allergyNote, physicalExam, advice)
                .anyMatch(section -> section != null && section.length() > MAX_SECTION_LENGTH)) {
            throw new InvalidClinicalDataException("RECORD_CONTENT_INVALID",
                    "Each examination section is limited to " + MAX_SECTION_LENGTH + " characters");
        }
    }

    /** A null section in {@code change} keeps the current text; an empty one clears it. */
    public ClinicalContent merge(ClinicalContent change) {
        if (change == null) {
            return this;
        }
        return new ClinicalContent(pick(chiefComplaint, change.chiefComplaint), pick(presentIllness, change.presentIllness),
                pick(pastHistory, change.pastHistory), pick(allergyNote, change.allergyNote),
                pick(physicalExam, change.physicalExam), pick(advice, change.advice));
    }

    private static String pick(String current, String change) {
        if (change == null) {
            return current;
        }
        return change.isEmpty() ? null : change;
    }
}
