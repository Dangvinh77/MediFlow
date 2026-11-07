package com.mediflow.organization.domain.model;

import com.mediflow.organization.domain.exception.InvalidRoomException;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;

/**
 * An entry of the administrator-managed room type catalogue. Each type belongs to one of the
 * three fixed department categories. Paraclinical and administrative units always work in a
 * single room; a clinical type may allow several rooms per department (consultation rooms).
 * {@link #CONSULTATION} is system-defined: it is the only type that hosts consultation sessions.
 */
public final class RoomTypeDefinition {

    public static final String CONSULTATION = "CONSULTATION";

    private final String code;
    private String name;
    private String description;
    private final DepartmentType departmentType;
    private final boolean singlePerDepartment;
    private final boolean systemDefined;
    private final int sortOrder;
    private final Instant createdAt;
    private Instant updatedAt;

    private RoomTypeDefinition(String code, String name, String description,
                               DepartmentType departmentType, boolean singlePerDepartment,
                               boolean systemDefined, int sortOrder, Instant createdAt,
                               Instant updatedAt) {
        if (code == null || !code.matches("[A-Z][A-Z0-9_]{1,39}")) {
            throw new InvalidRoomException("Room type code must be 2 to 40 upper-case characters");
        }
        if (departmentType == null) {
            throw new InvalidRoomException("Department category is required");
        }
        this.code = code;
        this.name = name(name);
        this.description = description(description);
        this.departmentType = departmentType;
        // Functional and paraclinical units work in exactly one room.
        this.singlePerDepartment = departmentType != DepartmentType.CLINICAL || singlePerDepartment;
        this.systemDefined = systemDefined;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** A new administrator-defined type; its code is derived from the name. */
    public static RoomTypeDefinition define(String name, String description,
                                            DepartmentType departmentType,
                                            boolean singlePerDepartment, int sortOrder) {
        return new RoomTypeDefinition(codeFor(name), name, description, departmentType,
                singlePerDepartment, false, sortOrder, Instant.now(), null);
    }

    public static RoomTypeDefinition reconstitute(String code, String name, String description,
                                                  DepartmentType departmentType,
                                                  boolean singlePerDepartment,
                                                  boolean systemDefined, int sortOrder,
                                                  Instant createdAt, Instant updatedAt) {
        return new RoomTypeDefinition(code, name, description, departmentType,
                singlePerDepartment, systemDefined, sortOrder, createdAt, updatedAt);
    }

    /** Category and room-count rule are fixed: existing rooms were validated against them. */
    public void rename(String name, String description) {
        this.name = name(name);
        this.description = description(description);
        this.updatedAt = Instant.now();
    }

    public boolean allowedFor(DepartmentType type) {
        return departmentType == type;
    }

    /** Only consultation rooms host sessions. */
    public boolean schedulable() {
        return CONSULTATION.equals(code);
    }

    /** Upper-case ASCII code from a Vietnamese name, e.g. "Phòng thủ thuật" → PHONG_THU_THUAT. */
    public static String codeFor(String name) {
        String plain = Normalizer.normalize(name == null ? "" : name.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D')
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (plain.isEmpty() || !Character.isLetter(plain.charAt(0))) plain = "TYPE_" + plain;
        return plain.length() > 40 ? plain.substring(0, 40).replaceAll("_+$", "") : plain;
    }

    private static String name(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() < 2 || trimmed.length() > 100) {
            throw new InvalidRoomException("Room type name must contain 2 to 100 characters");
        }
        return trimmed;
    }

    private static String description(String value) {
        String trimmed = value == null ? null : value.trim();
        if (trimmed != null && trimmed.length() > 255) {
            throw new InvalidRoomException("Room type description must not exceed 255 characters");
        }
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public DepartmentType getDepartmentType() { return departmentType; }
    public boolean isSinglePerDepartment() { return singlePerDepartment; }
    public boolean isSystemDefined() { return systemDefined; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
