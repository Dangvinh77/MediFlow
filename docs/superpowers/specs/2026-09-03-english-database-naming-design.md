# Thiết kế chuẩn hóa tên database sang tiếng Anh

## 1. Mục tiêu

Chuẩn hóa tên kỹ thuật còn dùng tiếng Việt không dấu trong ba bounded context `organization`, `patient` và `notification` sang tiếng Anh, để thống nhất với các database còn lại của MediFlow.

Phần diễn giải nghiệp vụ trong báo cáo vẫn sử dụng tiếng Việt. Tên bảng và cột PostgreSQL sử dụng tiếng Anh theo quy ước:

- Bảng: `UPPER_SNAKE_CASE`.
- Cột: `snake_case`.
- Không thay đổi tên database `mediflow_organization`, `mediflow_patient`, `mediflow_notification`.

## 2. Phạm vi

### Bao gồm

- Báo cáo Giai đoạn 2 và danh mục bảng/quan hệ trong báo cáo.
- ERD Organization, Patient và Notification.
- Các sơ đồ Giai đoạn 2 khác có hiển thị trực tiếp tên bảng tiếng Việt.
- Đặc tả backend và DDL trong:
  - `docs/eproject_general_plan/backend-spec/01-organization.md`.
  - `docs/eproject_general_plan/backend-spec/02-patient.md`.
  - `docs/eproject_general_plan/backend-spec/07-notification.md`.
- Tài liệu service trong:
  - `docs/ai/services/organization.md`.
  - `docs/ai/services/patient.md`.
  - `docs/ai/services/notification.md`.

### Không bao gồm

- Mã nguồn Java, DTO, API contract hoặc event contract đang triển khai.
- Migration SQL thực tế trong các module backend.
- Các database Clinical, Lab, Pharmacy, Billing và Report vì đã dùng tên tiếng Anh.
- Việc dịch phần mô tả nghiệp vụ tiếng Việt sang tiếng Anh.

## 3. Ánh xạ tên Organization database

### Bảng `DEPARTMENT`

| Tên cũ | Tên mới |
|---|---|
| `KHOA` | `DEPARTMENT` |
| `ma_khoa` | `department_id` |
| `ten_khoa` | `department_name` |
| `ma_viet_tat` | `abbreviation` |
| `loai_khoa` | `department_type` |
| `truong_khoa` | `department_head_id` |
| `dia_diem` | `location` |
| `hoat_dong` | `is_active` |

`created_at` và `updated_at` được giữ nguyên.

### Bảng `STAFF`

| Tên cũ | Tên mới |
|---|---|
| `NHAN_VIEN` | `STAFF` |
| `ma_nhan_vien` | `staff_id` |
| `ho_ten` | `full_name` |
| `ma_khoa` | `department_id` |
| `chuc_danh` | `job_title` |
| `chuyen_khoa` | `specialization` |
| `so_chung_chi` | `license_number` |
| `so_dien_thoai` | `phone_number` |
| `trang_thai` | `status` |

`email`, `created_at` và `updated_at` được giữ nguyên.

### Bảng `ACCOUNT`

| Tên cũ | Tên mới |
|---|---|
| `TAI_KHOAN` | `ACCOUNT` |
| `ma_tai_khoan` | `account_id` |
| `ten_dang_nhap` | `username` |
| `mat_khau_hash` | `password_hash` |
| `ma_nhan_vien` | `staff_id` |
| `vai_tro` | `role` |
| `kich_hoat` | `is_active` |
| `lan_dang_nhap_cuoi` | `last_login_at` |

`created_at` và `updated_at` được giữ nguyên.

## 4. Ánh xạ tên Patient database

| Tên cũ | Tên mới |
|---|---|
| `BENH_NHAN` | `PATIENT` |
| `ma_benh_nhan` | `patient_id` |
| `ho_ten` | `full_name` |
| `ngay_sinh` | `date_of_birth` |
| `gioi_tinh` | `gender` |
| `so_cmnd` | `identity_number` |
| `dia_chi` | `address` |
| `so_dien_thoai` | `phone_number` |
| `bhyt_so` | `health_insurance_number` |

`email`, `created_at` và `updated_at` được giữ nguyên.

## 5. Ánh xạ tên Notification database

### Bảng `NOTIFICATION`

| Tên cũ | Tên mới |
|---|---|
| `THONG_BAO` | `NOTIFICATION` |
| `ma_thong_bao` | `notification_id` |
| `ma_benh_nhan` | `patient_id` |
| `tieu_de` | `title` |
| `noi_dung` | `content` |
| `loai` | `channel` |
| `dia_chi_nhan` | `recipient_address` |
| `trang_thai` | `status` |
| `ly_do_that_bai` | `failure_reason` |
| `so_lan_thu` | `retry_count` |
| `ngay_tao` | `created_at` |
| `ngay_gui` | `sent_at` |

### Bảng `PROCESSED_EVENT`

| Tên cũ | Tên mới |
|---|---|
| `SU_KIEN_DA_XU_LY` | `PROCESSED_EVENT` |
| `xu_ly_luc` | `processed_at` |

`event_id` và `routing_key` được giữ nguyên.

## 6. Quan hệ và ràng buộc

- `STAFF.department_id` tham chiếu `DEPARTMENT.department_id` trong Organization database.
- `ACCOUNT.staff_id` tham chiếu `STAFF.staff_id` và được phép `NULL` đối với tài khoản bệnh nhân.
- `DEPARTMENT.department_head_id` tham chiếu `STAFF.staff_id` và được phép `NULL`.
- `NOTIFICATION.patient_id` chỉ là UUID tham chiếu logic sang Patient Service, không tạo khóa ngoại xuyên database.
- `PROCESSED_EVENT.event_id` tiếp tục là khóa chính dùng để bảo đảm consumer idempotent.

## 7. Cập nhật sơ đồ

Các nguồn Mermaid cần vẽ lại:

1. `02-erd-organization.mmd`.
2. `03-erd-patient.mmd`.
3. `08-erd-notification.mmd`.
4. `17-luong-thong-bao-bao-cao.mmd` vì đang hiển thị trực tiếp `THONG_BAO`.

Mỗi sơ đồ được xuất lại thành:

- Mermaid `.mmd` để chỉnh sửa.
- SVG tự chứa để chèn vào Word.
- PNG nền trắng, rộng 2400 px làm định dạng dự phòng.

## 8. Quy tắc đồng bộ tài liệu

- DDL, bảng ánh xạ persistence, mô tả quan hệ và ví dụ truy vấn phải dùng tên mới.
- Không thay đổi tên routing key như `patient.created`, `notification.sent`.
- Không dịch nội dung thông báo mẫu hoặc mô tả nghiệp vụ.
- Không thay đổi tên kỹ thuật đã dùng tiếng Anh đúng chuẩn.
- Mọi tham chiếu đến tên bảng cũ trong phạm vi tài liệu được chọn phải được thay thế hoặc ghi rõ là tên lịch sử trong bảng ánh xạ.

## 9. Kiểm tra hoàn tất

- Không còn tên bảng/cột tiếng Việt trong ERD Organization, Patient và Notification.
- Ba đặc tả backend có DDL và phần persistence thống nhất với ERD.
- Ba tài liệu service khai báo cùng một bộ tên bảng/cột.
- Bốn sơ đồ được render lại thành SVG và PNG hợp lệ.
- SVG Word-safe không chứa font ngoài, `@import`, CSS variable chưa giải quyết hoặc `color-mix()`.
- Tất cả liên kết tương đối trong báo cáo và README vẫn hợp lệ.

