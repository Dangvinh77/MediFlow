package com.mediflow.organization.domain.model;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.organization.domain.exception.InvalidAccountException;

/**
 * Domain model đại diện cho tài khoản đăng nhập hệ thống.
 *
 * Account chịu trách nhiệm:
 *
 * - username
 * - password hash
 * - staff liên kết
 * - role
 * - trạng thái active/inactive
 * - thời điểm login cuối
 *
 * Account KHÔNG chịu trách nhiệm:
 *
 * - tạo JWT
 * - verify HTTP request
 * - BCrypt implementation
 * - authentication filter
 *
 * Những việc đó thuộc Application / Infrastructure / Gateway.
 */
public class Account {

    /**
     * ID duy nhất của Account.
     */
    private final UUID accountId;

    /**
     * Username đăng nhập.
     *
     * Username phải UNIQUE.
     *
     * Việc enforce uniqueness cuối cùng phải được đảm bảo
     * bởi database UNIQUE constraint.
     */
    private final String username;

    /**
     * Password đã được hash.
     *
     * Tuyệt đối không lưu plaintext password.
     *
     * Ví dụ giá trị:
     *
     * $2a$10$...
     *
     * Domain chỉ lưu hash.
     *
     * Việc BCrypt hash/check cụ thể sẽ do PasswordHasher
     * ở Application/Infrastructure thực hiện.
     */
    private String passwordHash;

    /**
     * ID của Staff tương ứng với Account.
     *
     * PATIENT:
     * staffId = null
     *
     * Staff account:
     * staffId != null
     *
     * SYSTEM:
     * có thể không cần Staff.
     */
    private final UUID staffId;

    /**
     * Role của Account.
     */
    private final Role role;

    /**
     * Trạng thái Account.
     *
     * true = được phép đăng nhập
     * false = bị vô hiệu hóa
     */
    private boolean active;

    /**
     * Thời điểm đăng nhập thành công gần nhất.
     */
    private Instant lastLoginAt;

    /**
     * Thời điểm tạo Account.
     */
    private final Instant createdAt;

    /**
     * Thời điểm cập nhật Account gần nhất.
     */
    private Instant updatedAt;

    /**
     * Constructor dùng để khôi phục Account từ persistence.
     *
     * Constructor enforce các invariant liên quan đến
     * role và staffId.
     */
    public Account(
            UUID accountId,
            String username,
            String passwordHash,
            UUID staffId,
            Role role,
            boolean active,
            Instant lastLoginAt,
            Instant createdAt,
            Instant updatedAt) {
        validate(role, staffId);

        this.accountId = accountId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.staffId = staffId;
        this.role = role;
        this.active = active;
        this.lastLoginAt = lastLoginAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Factory method tạo Account mới.
     *
     * Lưu ý:
     *
     * passwordHash phải là HASH đã được tạo từ Application layer.
     *
     * Domain KHÔNG gọi BCrypt.
     */
    public static Account create(
            UUID accountId,
            String username,
            String passwordHash,
            UUID staffId,
            Role role) {
        Instant now = Instant.now();

        return new Account(
                accountId,
                username,
                passwordHash,
                staffId,
                role,
                true,
                null,
                now,
                now);
    }

    /**
     * Kiểm tra invariant của Account.
     *
     * PATIENT không được liên kết với Staff.
     *
     * Các Account dành cho nhân viên phải có staffId.
     *
     * SYSTEM là trường hợp đặc biệt:
     * có thể không gắn với Staff.
     */
    private void validate(
            Role role,
            UUID staffId) {
        if (role == null) {
            throw new InvalidAccountException(
                    "Account role cannot be null");
        }

        /*
         * Patient không phải nhân viên.
         * Vì vậy PATIENT không được có staffId.
         */
        if (role == Role.PATIENT && staffId != null) {
            throw new InvalidAccountException(
                    "PATIENT account must not have staffId");
        }

        /*
         * Các role của nhân viên phải liên kết với Staff.
         *
         * SYSTEM là tài khoản kỹ thuật nên không bắt buộc
         * phải có Staff.
         */
        if (role != Role.PATIENT
                && role != Role.SYSTEM
                && staffId == null) {

            throw new InvalidAccountException(
                    "Staff account must have staffId");
        }
    }

    /**
     * Kích hoạt Account.
     *
     * Sau khi activate, Account có thể đăng nhập lại.
     */
    public void activate() {
        this.active = true;

        touch();
    }

    /**
     * Vô hiệu hóa Account.
     *
     * Sau khi deactivate:
     *
     * - Login tiếp theo phải bị từ chối.
     *
     * JWT đã cấp trước đó không nhất thiết phải revoke ngay.
     * Theo technical design hiện tại, JWT sẽ hết hạn tự nhiên.
     */
    public void deactivate() {
        this.active = false;

        touch();
    }

    /**
     * Ghi nhận một lần đăng nhập thành công.
     *
     * Method này chỉ được gọi sau khi Application layer
     * đã verify password thành công.
     *
     * Domain không tự check BCrypt.
     */
    public void recordLogin() {

        /*
         * Account inactive không được ghi nhận login thành công.
         */
        if (!active) {
            throw new InvalidAccountException(
                    "Inactive account cannot login");
        }

        this.lastLoginAt = Instant.now();

        touch();
    }

    /**
     * Cập nhật password hash.
     *
     * Password mới phải được hash trước khi truyền vào đây.
     *
     * Domain không biết BCrypt là gì.
     */
    public void changePassword(String newPasswordHash) {

        if (newPasswordHash == null
                || newPasswordHash.isBlank()) {

            throw new InvalidAccountException(
                    "Password hash cannot be blank");
        }

        this.passwordHash = newPasswordHash;

        touch();
    }

    /**
     * Cập nhật updatedAt.
     */
    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UUID getStaffId() {
        return staffId;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
