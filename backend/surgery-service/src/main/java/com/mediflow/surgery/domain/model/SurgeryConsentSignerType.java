package com.mediflow.surgery.domain.model;

/** Describes the declared signer category; it does not authorize or verify that signer. */
public enum SurgeryConsentSignerType {
    PATIENT,
    GUARDIAN,
    AUTHORIZED_REPRESENTATIVE
}
