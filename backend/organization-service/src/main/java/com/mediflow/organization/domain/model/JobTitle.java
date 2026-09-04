package com.mediflow.organization.domain.model;

/**
 * Chức danh/nghề nghiệp của một nhân viên trong bệnh viện.
 *
 * Lưu ý:
 * JobTitle mô tả "người này làm nghề gì".
 *
 * Nó khác với Role.
 * Role mô tả "tài khoản này có quyền gì trong hệ thống".
 */
public enum JobTitle {

    DOCTOR,

    NURSE,

    TECHNICIAN,

    PHARMACIST,

    CASHIER,

    MANAGER,

    ADMINISTRATIVE
}