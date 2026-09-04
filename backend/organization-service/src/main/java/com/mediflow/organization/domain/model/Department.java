package com.mediflow.organization.domain.model;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.organization.domain.exception.DepartmentHasActiveStaffException;
import com.mediflow.organization.domain.exception.InvalidDepartmentHeadException;

/**
 * Domain model đại diện cho một khoa/phòng trong bệnh viện.
 *
 * Department chịu trách nhiệm quản lý các thông tin thuộc về
 * cơ cấu tổ chức:
 *
 * - Khoa có ID gì?
 * - Tên khoa là gì?
 * - Mã viết tắt là gì?
 * - Loại khoa là gì?
 * - Ai là trưởng khoa?
 * - Khoa nằm ở đâu?
 * - Khoa còn hoạt động không?
 *
 * Department KHÔNG chịu trách nhiệm:
 *
 * - Bệnh nhân
 * - Lịch hẹn
 * - Hồ sơ bệnh án
 * - Xét nghiệm
 * - Đơn thuốc
 * - Thanh toán
 *
 * Đây là nguyên tắc Bounded Context:
 * Organization Service chỉ quản lý "ai" và "ở đâu".
 */
public class Department {

    /**
     * ID duy nhất của khoa.
     *
     * Đây là primary identity của Department.
     */
    private final UUID departmentId;

    /**
     * Tên đầy đủ của khoa.
     *
     * Ví dụ:
     * "Khoa Nội tổng hợp"
     */
    private String departmentName;

    /**
     * Mã viết tắt của khoa.
     *
     * Ví dụ:
     * "NOI"
     * "NGOAI"
     * "XN"
     */
    private String abbreviation;

    /**
     * Phân loại khoa:
     * CLINICAL / PARACLINICAL / ADMINISTRATIVE.
     */
    private DepartmentType departmentType;

    /**
     * ID của Staff đang giữ chức trưởng khoa.
     *
     * Chỉ lưu UUID thay vì giữ trực tiếp object Staff.
     *
     * Lý do:
     * Department và Staff là hai domain object độc lập.
     * Domain không cần tạo object graph Department -> Staff -> Department.
     */
    private UUID departmentHeadId;

    /**
     * Vị trí vật lý của khoa.
     *
     * Ví dụ:
     * "Tầng 3 - Tòa A"
     */
    private String location;

    /**
     * Cho biết khoa còn hoạt động hay không.
     *
     * true = đang hoạt động
     * false = đã ngừng hoạt động
     */
    private boolean active;

    /**
     * Thời điểm tạo Department.
     */
    private final Instant createdAt;

    /**
     * Thời điểm cập nhật Department gần nhất.
     */
    private Instant updatedAt;

    /**
     * Constructor dùng để khôi phục Department từ persistence.
     *
     * Infrastructure/persistence có thể sử dụng constructor này
     * để chuyển dữ liệu database thành domain object.
     */
    private Department(
            UUID departmentId,
            String departmentName,
            String abbreviation,
            DepartmentType departmentType,
            UUID departmentHeadId,
            String location,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.abbreviation = abbreviation;
        this.departmentType = departmentType;
        this.departmentHeadId = departmentHeadId;
        this.location = location;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Factory method dùng để tạo một Department mới.
     *
     * Dùng factory giúp code phía Application dễ đọc hơn
     * thay vì phải truyền active, createdAt, updatedAt thủ công.
     */
    public static Department create(
            UUID departmentId,
            String departmentName,
            String abbreviation,
            DepartmentType departmentType,
            String location) {
        Instant now = Instant.now();

        return new Department(
                departmentId,
                departmentName,
                abbreviation,
                departmentType,
                null,
                location,
                true,
                now,
                now);
    }

    /**
     * Reconstruct Department từ persistence data.
     *
     * Method này phục vụ Infrastructure khi load
     * aggregate từ database.
     *
     * Không phải business operation.
     */
    public static Department reconstitute(
            UUID departmentId,
            String departmentName,
            String abbreviation,
            DepartmentType departmentType,
            UUID departmentHeadId,
            String location,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
        return new Department(
                departmentId,
                departmentName,
                abbreviation,
                departmentType,
                departmentHeadId,
                location,
                active,
                createdAt,
                updatedAt);
    }

    

    /**
     * Cập nhật thông tin cơ bản của khoa.
     *
     * Method này KHÔNG cho phép thay đổi:
     * - departmentId
     * - departmentHeadId
     * - active
     *
     * Vì đây là những state có business rule riêng.
     */
    public void update(
            String departmentName,
            String abbreviation,
            DepartmentType departmentType,
            String location) {
        this.departmentName = departmentName;
        this.abbreviation = abbreviation;
        this.departmentType = departmentType;
        this.location = location;

        touch();
    }

    /**
     * Thay đổi trưởng khoa.
     *
     * Business rule:
     *
     * - Staff phải tồn tại.
     * - Staff phải thuộc chính Department này.
     * - Staff phải đang ACTIVE.
     * - Staff phải có chức danh phù hợp.
     *
     * Việc kiểm tra Staff tồn tại/thuộc khoa sẽ được Application layer
     * lấy từ StaffRepository trước khi gọi method này.
     *
     * Domain không được tự gọi repository.
     */
    public void changeHead(Staff staff) {

        if (staff == null) {
            throw new InvalidDepartmentHeadException(
                    "Department head cannot be null");
        }

        /*
         * Trưởng khoa phải là nhân viên đang hoạt động.
         */
        if (!staff.isActive()) {
            throw new InvalidDepartmentHeadException(
                    "Department head must be active");
        }

        /*
         * Trưởng khoa phải thuộc chính khoa này.
         */
        if (!departmentId.equals(staff.getDepartmentId())) {
            throw new InvalidDepartmentHeadException(
                    "Staff does not belong to this department");
        }

        /*
         * Trong design hiện tại:
         * trưởng khoa được giả định là DOCTOR.
         *
         * Nếu sau này nghiệp vụ cho phép Nurse/Manager làm trưởng khoa,
         * chỉ cần thay đổi rule này.
         */
        if (staff.getJobTitle() != JobTitle.DOCTOR) {
            throw new InvalidDepartmentHeadException(
                    "Department head must be a doctor");
        }

        this.departmentHeadId = staff.getStaffId();

        touch();
    }

    /**
     * Xóa trưởng khoa hiện tại.
     *
     * Không xóa Staff.
     * Chỉ xóa liên kết departmentHeadId.
     */
    public void removeHead() {
        this.departmentHeadId = null;

        touch();
    }

    /**
     * Vô hiệu hóa khoa.
     *
     * Business rule:
     *
     * Không được ngừng hoạt động khoa nếu vẫn còn
     * nhân viên ACTIVE đang làm việc tại khoa.
     *
     * Domain không tự query StaffRepository.
     * Application layer phải kiểm tra trước và truyền kết quả vào.
     */
    public void deactivate(boolean hasActiveStaff) {

        if (hasActiveStaff) {
            throw new DepartmentHasActiveStaffException(
                    departmentId);
        }

        this.active = false;

        touch();
    }

    /**
     * Kích hoạt lại khoa.
     */
    public void activate() {
        this.active = true;

        touch();
    }

    /**
     * Cập nhật updatedAt mỗi khi Department thay đổi state.
     */
    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public DepartmentType getDepartmentType() {
        return departmentType;
    }

    public UUID getDepartmentHeadId() {
        return departmentHeadId;
    }

    public String getLocation() {
        return location;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
