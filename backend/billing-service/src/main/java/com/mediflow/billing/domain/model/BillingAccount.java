package com.mediflow.billing.domain.model;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.billing.domain.exception.BillingRuleException;

import lombok.Getter;

/**
 * Sổ tài khoản viện phí cho đúng một đợt điều trị (V2 ledger, additive — xem
 * backend-spec/care-finance-v2/06-billing.md §3). Chưa được đấu nối vào flow chạy thật; đây là
 * bước 1/nhiều theo lộ trình rollout §10 ("Add ledger schema and repository tests without changing
 * CURRENT saga"). Tính duy nhất (careEpisodeType, careEpisodeId) là ràng buộc DB
 * ({@code uq_billing_account_episode}) + trách nhiệm của application service mở/tái sử dụng tài
 * khoản — không phải quy tắc domain thuần túy nên không lặp lại ở đây.
 */
@Getter
public class BillingAccount {

    private final UUID accountId;
    private final UUID patientId;
    private final UUID departmentId;
    private final CareEpisodeType careEpisodeType;
    private final UUID careEpisodeId;
    private AccountStatus status;
    private final String currency;
    private final long version;
    private final Instant openedAt;
    private Instant chargeClosedAt;
    private Instant closedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private BillingAccount(UUID accountId, UUID patientId, UUID departmentId,
                            CareEpisodeType careEpisodeType, UUID careEpisodeId, AccountStatus status,
                            String currency, long version, Instant openedAt, Instant chargeClosedAt,
                            Instant closedAt, Instant createdAt, Instant updatedAt) {
        this.accountId = accountId;
        this.patientId = patientId;
        this.departmentId = departmentId;
        this.careEpisodeType = careEpisodeType;
        this.careEpisodeId = careEpisodeId;
        this.status = status;
        this.currency = currency;
        this.version = version;
        this.openedAt = openedAt;
        this.chargeClosedAt = chargeClosedAt;
        this.closedAt = closedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Mở tài khoản mới cho một đợt điều trị — luôn bắt đầu ở {@code OPEN}. */
    public static BillingAccount open(UUID patientId, UUID departmentId, CareEpisodeType careEpisodeType,
                                       UUID careEpisodeId, String currency, Instant openedAt) {
        requireNonNull(patientId, "BILLING_ACCOUNT_PATIENT_REQUIRED", "patientId là bắt buộc");
        requireNonNull(departmentId, "BILLING_ACCOUNT_DEPARTMENT_REQUIRED", "departmentId là bắt buộc");
        requireNonNull(careEpisodeType, "BILLING_ACCOUNT_EPISODE_TYPE_REQUIRED", "careEpisodeType là bắt buộc");
        requireNonNull(careEpisodeId, "BILLING_ACCOUNT_EPISODE_ID_REQUIRED", "careEpisodeId là bắt buộc");
        requireNonNull(currency, "BILLING_ACCOUNT_CURRENCY_REQUIRED", "currency là bắt buộc");
        return new BillingAccount(null, patientId, departmentId, careEpisodeType, careEpisodeId,
                AccountStatus.OPEN, currency, 0L, openedAt, null, null, null, null);
    }

    /** Dựng lại từ dữ liệu đã lưu — không chạy lại quy tắc lúc mở. */
    public static BillingAccount restore(UUID accountId, UUID patientId, UUID departmentId,
                                          CareEpisodeType careEpisodeType, UUID careEpisodeId,
                                          AccountStatus status, String currency, long version,
                                          Instant openedAt, Instant chargeClosedAt, Instant closedAt,
                                          Instant createdAt, Instant updatedAt) {
        return new BillingAccount(accountId, patientId, departmentId, careEpisodeType, careEpisodeId,
                status, currency, version, openedAt, chargeClosedAt, closedAt, createdAt, updatedAt);
    }

    /** Khóa nhận phí mới sau khi bệnh nhân xuất viện được duyệt y khoa (OPEN → CHARGE_CLOSED). */
    public void closeCharges(Instant at) {
        transitionTo(AccountStatus.CHARGE_CLOSED);
        this.chargeClosedAt = at;
    }

    /** Bắt đầu chờ quyết toán sau khi đã khóa phí (CHARGE_CLOSED → SETTLEMENT_PENDING). */
    public void beginSettlement() {
        transitionTo(AccountStatus.SETTLEMENT_PENDING);
    }

    /** Hoàn tất quyết toán (SETTLEMENT_PENDING → SETTLED). */
    public void settle() {
        transitionTo(AccountStatus.SETTLED);
    }

    /** Đóng hẳn tài khoản sau khi đã quyết toán (SETTLED → CLOSED). */
    public void close(Instant at) {
        transitionTo(AccountStatus.CLOSED);
        this.closedAt = at;
    }

    public boolean isOpenForCharges() {
        return status == AccountStatus.OPEN;
    }

    private void transitionTo(AccountStatus next) {
        if (!isValidTransition(status, next)) {
            throw new BillingRuleException("BILLING_INVALID_ACCOUNT_TRANSITION",
                    "Không thể chuyển trạng thái tài khoản từ " + status + " sang " + next);
        }
        this.status = next;
    }

    private static boolean isValidTransition(AccountStatus from, AccountStatus to) {
        return switch (from) {
            case OPEN -> to == AccountStatus.CHARGE_CLOSED;
            case CHARGE_CLOSED -> to == AccountStatus.SETTLEMENT_PENDING;
            case SETTLEMENT_PENDING -> to == AccountStatus.SETTLED;
            case SETTLED -> to == AccountStatus.CLOSED;
            case CLOSED -> false;
        };
    }

    private static void requireNonNull(Object value, String code, String message) {
        if (value == null) {
            throw new BillingRuleException(code, message);
        }
    }
}
