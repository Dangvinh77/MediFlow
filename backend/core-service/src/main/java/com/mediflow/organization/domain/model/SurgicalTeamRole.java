package com.mediflow.organization.domain.model;

/** Eligibility categories, deliberately separate from account authorization roles. */
public enum SurgicalTeamRole {
    PRIMARY_SURGEON, ASSISTANT_SURGEON, ANESTHESIOLOGIST, OR_NURSE;

    public boolean accepts(Staff staff) {
        if (staff == null || !staff.isActive()) return false;
        if (this == OR_NURSE) return staff.getJobTitle() == JobTitle.NURSE;
        return staff.getJobTitle() == JobTitle.DOCTOR
                && staff.getLicenseNumber() != null && !staff.getLicenseNumber().isBlank();
    }
}
