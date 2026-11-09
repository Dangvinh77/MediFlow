package com.mediflow.clinical.application.dto.authority;

import java.util.UUID;

/**
 * Outcome of the record read policy.
 *
 * @param nurseDepartmentId set when a nurse may see only the records of this department
 */
public record RecordReadDecision(boolean allowed, String basis, String denialCode, UUID nurseDepartmentId) {
    public static RecordReadDecision allow(String basis) {
        return new RecordReadDecision(true, basis, null, null);
    }

    public static RecordReadDecision allowDepartment(String basis, UUID departmentId) {
        return new RecordReadDecision(true, basis, null, departmentId);
    }

    public static RecordReadDecision deny(String denialCode) {
        return new RecordReadDecision(false, null, denialCode, null);
    }
}
