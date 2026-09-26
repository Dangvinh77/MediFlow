package com.mediflow.pharmacy.domain.model.enums;

/** Distinguishes the identity kind recorded for a completed dispense. */
public enum DispenseActorType {
    /** A verified employee identity from the signed staff claim. */
    STAFF,
    /** A verified account identity with an allowed administrative role. */
    ACCOUNT,
    /** An automatic workflow with no human identity. */
    SYSTEM,
    /** A historical row whose actor kind cannot be recovered safely. */
    LEGACY_UNKNOWN
}
