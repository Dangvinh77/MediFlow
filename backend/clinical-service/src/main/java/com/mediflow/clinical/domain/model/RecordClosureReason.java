package com.mediflow.clinical.domain.model;

/** Why a visit ended without a clinical conclusion. */
public enum RecordClosureReason {
    PATIENT_LEFT,
    PATIENT_REFUSED_EXAM,
    PATIENT_REFUSED_ORDER,
    OTHER
}
