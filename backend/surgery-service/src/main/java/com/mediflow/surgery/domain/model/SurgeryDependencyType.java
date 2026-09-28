package com.mediflow.surgery.domain.model;

/** Inputs whose exact versions were observed by a readiness decision. */
public enum SurgeryDependencyType {
    INDICATION,
    CHECKLIST,
    SURGERY_CONSENT,
    ANESTHESIA_CONSENT,
    FINANCIAL_CLEARANCE,
    TEAM_ELIGIBILITY,
    SCHEDULE
}
