package com.mediflow.billing.domain.model;

/**
 * Mục đích một {@link FinancialClearance} cấp quyền — quyết định trường target nào bắt buộc
 * (khớp {@code ck_clearance_target}, backend-spec/care-finance-v2/06-billing.md §3).
 */
public enum ClearancePurpose { EXAM, LAB_TEST, PRESCRIPTION, ADMISSION_DEPOSIT, SURGERY }
