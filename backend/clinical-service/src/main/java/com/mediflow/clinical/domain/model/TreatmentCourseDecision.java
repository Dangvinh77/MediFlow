package com.mediflow.clinical.domain.model;

/** Why a doctor ended a treatment course. Courses never close by themselves. */
public enum TreatmentCourseDecision {
    RESOLVED,
    TRANSFERRED,
    PATIENT_STOPPED,
    OTHER
}
