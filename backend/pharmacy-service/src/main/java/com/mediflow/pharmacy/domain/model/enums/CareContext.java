package com.mediflow.pharmacy.domain.model.enums;

/** Care setting assigned by the source workflow; never infer it from patient history. */
public enum CareContext {
    OUTPATIENT,
    ADMISSION
}
