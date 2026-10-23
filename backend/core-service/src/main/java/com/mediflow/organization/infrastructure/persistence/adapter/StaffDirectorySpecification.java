package com.mediflow.organization.infrastructure.persistence.adapter;

import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.StaffStatus;
import com.mediflow.organization.infrastructure.persistence.entity.DepartmentEntity;
import com.mediflow.organization.infrastructure.persistence.entity.StaffEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Literal, accent-insensitive directory search; pagination and filtering happen in PostgreSQL. */
final class StaffDirectorySpecification {
    private static final String ACCENTED = "àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ";
    private static final String PLAIN = "a".repeat(17) + "e".repeat(11) + "i".repeat(5) + "o".repeat(17) + "u".repeat(11) + "y".repeat(5) + "d";
    private static final Map<JobTitle, String> TITLES = Map.of(
            JobTitle.DOCTOR, "bac si", JobTitle.NURSE, "dieu duong", JobTitle.TECHNICIAN, "ky thuat vien",
            JobTitle.PHARMACIST, "duoc si", JobTitle.CASHIER, "thu ngan", JobTitle.MANAGER, "quan ly", JobTitle.ADMINISTRATIVE, "hanh chinh");

    private StaffDirectorySpecification() { }

    static Specification<StaffEntity> matching(UUID departmentId, JobTitle jobTitle, String keyword, boolean activeOnly, boolean includeIdentity) {
        String normalized = keyword == null ? "" : Normalizer.normalize(keyword.toLowerCase(Locale.ROOT).trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd');
        String pattern = "%" + normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return (root, query, cb) -> {
            var filters = new ArrayList<Predicate>();
            if (departmentId != null) filters.add(cb.equal(root.get("departmentId"), departmentId));
            if (jobTitle != null) filters.add(cb.equal(root.get("jobTitle"), jobTitle));
            if (activeOnly) filters.add(cb.equal(root.get("status"), StaffStatus.ACTIVE));
            if (!normalized.isEmpty()) {
                var department = query.subquery(UUID.class);
                var unit = department.from(DepartmentEntity.class);
                department.select(unit.get("departmentId")).where(cb.or(
                        cb.like(fold(cb, unit.get("departmentName")), pattern, '\\'),
                        cb.like(fold(cb, unit.get("abbreviation")), pattern, '\\')));
                var matches = new ArrayList<Predicate>();
                matches.add(cb.like(fold(cb, root.get("fullName")), pattern, '\\'));
                if (includeIdentity) matches.add(cb.like(root.get("identityNumber"), pattern, '\\'));
                matches.add(root.get("departmentId").in(department));
                TITLES.forEach((title, label) -> {
                    if (label.contains(normalized) || title.name().toLowerCase(Locale.ROOT).contains(normalized)) matches.add(cb.equal(root.get("jobTitle"), title));
                });
                filters.add(cb.or(matches.toArray(Predicate[]::new)));
            }
            return cb.and(filters.toArray(Predicate[]::new));
        };
    }

    private static Expression<String> fold(CriteriaBuilder cb, Expression<String> field) {
        return cb.function("translate", String.class, cb.lower(field), cb.literal(ACCENTED), cb.literal(PLAIN));
    }
}
