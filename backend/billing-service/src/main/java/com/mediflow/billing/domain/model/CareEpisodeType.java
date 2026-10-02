package com.mediflow.billing.domain.model;

/**
 * Loại đợt điều trị làm chủ khóa cho một {@link BillingAccount} — mỗi cặp
 * (careEpisodeType, careEpisodeId) chỉ mở đúng một tài khoản (xem
 * backend-spec/care-finance-v2/06-billing.md §3, {@code uq_billing_account_episode}).
 */
public enum CareEpisodeType { OUTPATIENT_VISIT, ADMISSION }
