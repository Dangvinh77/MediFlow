package com.mediflow.organization.domain.model;
/**
 * Role dùng để xác định vai trò của ACCOUNT khi đăng nhập hệ thống.
 *
 * Role thường được Gateway đưa vào JWT để các service khác
 * xác định người dùng hiện tại có quyền thực hiện thao tác hay không.
 *
 * Ví dụ:
 *
 * username = "doctor.nguyen"
 * role = DOCTOR
 *
 * Gateway có thể tạo JWT:
 *
 * {
 * "sub": "...",
 * "role": "DOCTOR",
 * "departmentId": "..."
 * }
 *
 * Lưu ý:
 * Role không phải là JobTitle.
 * Một Staff có JobTitle = DOCTOR,
 * nhưng quyền hệ thống được xác định bởi Account.role.
 */
public enum Role {

    ADMIN,

    DOCTOR,

    NURSE,

    PHARMACIST,

    CASHIER,

    LAB_TECH,

    MANAGER,

    PATIENT,

    SYSTEM
}