package com.mediflow.organization.domain.model;

/**
 * Workplace room catalogue, modelled on the Vietnamese hospital layout.
 *
 * <ul>
 *   <li>{@link #CONSULTATION}: a consultation room (buồng khám) planned for one clinical
 *       department; a department may own many and only these host consultation sessions.</li>
 *   <li>{@link #DEPARTMENT_OFFICE}: the shared office of a clinical department (văn phòng khoa,
 *       giao ban). Symbolic only: it never hosts sessions.</li>
 *   <li>Functional workplaces ({@link #LABORATORY}, {@link #PHARMACY}, {@link #CASHIER},
 *       {@link #NURSING}, {@link #ADMINISTRATION}): the single room where a non-clinical unit
 *       works. Every member of the unit works there; no head is appointed.</li>
 * </ul>
 */
public enum RoomType {
    CONSULTATION,
    DEPARTMENT_OFFICE,
    LABORATORY,
    PHARMACY,
    CASHIER,
    NURSING,
    ADMINISTRATION;

    /** Whether a department of the given type may own a room of this type. */
    public boolean allowedFor(DepartmentType departmentType) {
        if (departmentType == null) return false;
        return switch (this) {
            case CONSULTATION, DEPARTMENT_OFFICE -> departmentType == DepartmentType.CLINICAL;
            case LABORATORY, PHARMACY -> departmentType == DepartmentType.PARACLINICAL;
            case CASHIER, NURSING, ADMINISTRATION -> departmentType == DepartmentType.ADMINISTRATIVE;
        };
    }

    /** Consultation rooms are the only type a department may own several active rooms of. */
    public boolean singlePerDepartment() {
        return this != CONSULTATION;
    }

    /** Functional units work in exactly one room, whatever its type. */
    public boolean functionalWorkplace() {
        return this != CONSULTATION && this != DEPARTMENT_OFFICE;
    }
}
