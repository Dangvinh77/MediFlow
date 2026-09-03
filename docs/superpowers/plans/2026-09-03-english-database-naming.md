# English Database Naming Documentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Chuẩn hóa tên bảng và cột còn dùng tiếng Việt trong tài liệu Organization, Patient và Notification sang tiếng Anh, đồng thời render lại mọi sơ đồ Giai đoạn 2 bị ảnh hưởng.

**Architecture:** Chỉ thay đổi lớp tài liệu thiết kế: báo cáo Giai đoạn 2, Mermaid source và hai bộ đặc tả service. Bộ tên chuẩn lấy từ `docs/superpowers/specs/2026-09-03-english-database-naming-design.md`; database tiếp tục dùng bảng `UPPER_SNAKE_CASE` và cột `snake_case`, không sửa mã Java hoặc migration đang chạy.

**Tech Stack:** Markdown, Mermaid ER diagram/flowchart, Pretty Mermaid local renderer, SVG, PNG, PowerShell validation.

---

## File map

**Modify — báo cáo và nguồn sơ đồ:**

- `docs/monitor_proj_progress/02-giai-doan-2-thiet-ke-giao-dien-tieu-chuan/giai-doan-2-design.md` — danh mục bảng, quan hệ, tham chiếu logic và chú thích DDL.
- `docs/monitor_proj_progress/02-giai-doan-2-thiet-ke-giao-dien-tieu-chuan/assets/diagrams/src/02-erd-organization.mmd` — ERD `DEPARTMENT`, `STAFF`, `ACCOUNT`.
- `docs/monitor_proj_progress/02-giai-doan-2-thiet-ke-giao-dien-tieu-chuan/assets/diagrams/src/03-erd-patient.mmd` — ERD `PATIENT`.
- `docs/monitor_proj_progress/02-giai-doan-2-thiet-ke-giao-dien-tieu-chuan/assets/diagrams/src/08-erd-notification.mmd` — ERD `NOTIFICATION`, `PROCESSED_EVENT`.
- `docs/monitor_proj_progress/02-giai-doan-2-thiet-ke-giao-dien-tieu-chuan/assets/diagrams/src/17-luong-thong-bao-bao-cao.mmd` — đổi nhãn kho lịch sử từ `THONG_BAO` thành `NOTIFICATION`.

**Modify — đặc tả backend:**

- `docs/eproject_general_plan/backend-spec/01-organization.md` — DDL và tham chiếu persistence Organization.
- `docs/eproject_general_plan/backend-spec/02-patient.md` — DDL và tham chiếu persistence Patient.
- `docs/eproject_general_plan/backend-spec/07-notification.md` — DDL và tham chiếu persistence Notification.
- `docs/ai/services/organization.md` — xác nhận bộ tên chuẩn đã thống nhất, sửa mọi tên cũ còn sót.
- `docs/ai/services/patient.md` — xác nhận bộ tên chuẩn đã thống nhất, sửa mọi tên cũ còn sót.
- `docs/ai/services/notification.md` — thay mô tả bảng/cột tiếng Việt bằng tên tiếng Anh.

**Regenerate — không sửa thủ công:**

- `assets/diagrams/svg/{02-erd-organization,03-erd-patient,08-erd-notification,17-luong-thong-bao-bao-cao}.svg`.
- `assets/diagrams/word-svg/{02-erd-organization,03-erd-patient,08-erd-notification,17-luong-thong-bao-bao-cao}.svg`.
- `assets/diagrams/png/{02-erd-organization,03-erd-patient,08-erd-notification,17-luong-thong-bao-bao-cao}.png`.

---

### Task 1: Chuẩn hóa báo cáo Giai đoạn 2

**Files:**

- Modify: `docs/monitor_proj_progress/02-giai-doan-2-thiet-ke-giao-dien-tieu-chuan/giai-doan-2-design.md`

- [ ] **Step 1: Thay danh mục bảng của ba database**

Trong mục “Các bảng chính”, thay đúng sáu tên:

```text
KHOA              -> DEPARTMENT
NHAN_VIEN         -> STAFF
TAI_KHOAN         -> ACCOUNT
BENH_NHAN         -> PATIENT
THONG_BAO         -> NOTIFICATION
SU_KIEN_DA_XU_LY  -> PROCESSED_EVENT
```

- [ ] **Step 2: Thay tên trong bảng quan hệ**

Đặt các quan hệ nội service thành:

```text
STAFF.department_id -> DEPARTMENT.department_id
ACCOUNT.staff_id -> STAFF.staff_id
DEPARTMENT.department_head_id -> STAFF.staff_id
```

Đặt tham chiếu logic Notification thành:

```text
NOTIFICATION.patient_id -> Patient Service
```

- [ ] **Step 3: Ghi rõ phạm vi DDL**

Bổ sung một câu ngay trước hoặc sau phần DDL:

```text
Tên vật lý trong tài liệu dùng tiếng Anh; bảng dùng UPPER_SNAKE_CASE và cột dùng snake_case. Thay đổi này là chuẩn hóa hồ sơ thiết kế, chưa phải migration dữ liệu của môi trường đang chạy.
```

- [ ] **Step 4: Kiểm tra tên cũ không còn trong báo cáo**

Run:

```powershell
rg -n 'KHOA|NHAN_VIEN|TAI_KHOAN|BENH_NHAN|THONG_BAO|SU_KIEN_DA_XU_LY' docs/monitor_proj_progress/02-giai-doan-2-thiet-ke-giao-dien-tieu-chuan/giai-doan-2-design.md
```

Expected: không có kết quả kỹ thuật; từ “khoa” trong câu tiếng Việt thông thường không thuộc biểu thức chữ hoa này.

- [ ] **Step 5: Commit báo cáo**

```powershell
git add -- docs/monitor_proj_progress/02-giai-doan-2-thiet-ke-giao-dien-tieu-chuan/giai-doan-2-design.md
git commit -m "docs: normalize stage two database names"
```

---

### Task 2: Viết lại ba ERD và sơ đồ Notification–Report

**Files:**

- Modify: `docs/monitor_proj_progress/02-giai-doan-2-thiet-ke-giao-dien-tieu-chuan/assets/diagrams/src/02-erd-organization.mmd`
- Modify: `docs/monitor_proj_progress/02-giai-doan-2-thiet-ke-giao-dien-tieu-chuan/assets/diagrams/src/03-erd-patient.mmd`
- Modify: `docs/monitor_proj_progress/02-giai-doan-2-thiet-ke-giao-dien-tieu-chuan/assets/diagrams/src/08-erd-notification.mmd`
- Modify: `docs/monitor_proj_progress/02-giai-doan-2-thiet-ke-giao-dien-tieu-chuan/assets/diagrams/src/17-luong-thong-bao-bao-cao.mmd`

- [ ] **Step 1: Thay ERD Organization bằng schema tiếng Anh**

Các entity và cột phải đúng tập sau:

```text
DEPARTMENT(department_id, department_name, abbreviation, department_type,
           department_head_id, location, is_active, created_at, updated_at)
STAFF(staff_id, full_name, department_id, job_title, specialization,
      license_number, phone_number, email, status, created_at, updated_at)
ACCOUNT(account_id, username, password_hash, staff_id, role, is_active,
        last_login_at, created_at, updated_at)
```

Cardinality giữ nguyên: Department–Staff `1:N`, Staff–Account `1:0..1`, Department–Department head `0..1:0..1`.

- [ ] **Step 2: Thay ERD Patient bằng schema tiếng Anh**

```text
PATIENT(patient_id, full_name, date_of_birth, gender, identity_number,
        address, phone_number, email, health_insurance_number,
        created_at, updated_at)
```

- [ ] **Step 3: Thay ERD Notification bằng schema tiếng Anh**

```text
NOTIFICATION(notification_id, patient_id, title, content, channel,
             recipient_address, status, failure_reason, retry_count,
             created_at, sent_at)
PROCESSED_EVENT(event_id, routing_key, processed_at)
```

Không vẽ foreign key vật lý giữa `NOTIFICATION.patient_id` và Patient database.

- [ ] **Step 4: Sửa nhãn sơ đồ Notification–Report**

Thay:

```text
THONG_BAO -> NOTIFICATION
```

Giữ mô tả “Lịch sử gửi” bằng tiếng Việt.

- [ ] **Step 5: Kiểm tra nguồn Mermaid không còn tên cũ**

Run:

```powershell
rg -n 'KHOA|NHAN_VIEN|TAI_KHOAN|BENH_NHAN|THONG_BAO|SU_KIEN_DA_XU_LY|ma_khoa|ma_benh_nhan|ma_thong_bao' docs/monitor_proj_progress/02-giai-doan-2-thiet-ke-giao-dien-tieu-chuan/assets/diagrams/src/{02-erd-organization,03-erd-patient,08-erd-notification,17-luong-thong-bao-bao-cao}.mmd
```

Expected: không có kết quả.

- [ ] **Step 6: Commit Mermaid source**

```powershell
git add -- docs/monitor_proj_progress/02-giai-doan-2-thiet-ke-giao-dien-tieu-chuan/assets/diagrams/src
git commit -m "docs: translate remaining database diagrams"
```

---

### Task 3: Đồng bộ đặc tả Organization và Patient

**Files:**

- Modify: `docs/eproject_general_plan/backend-spec/01-organization.md`
- Modify: `docs/eproject_general_plan/backend-spec/02-patient.md`
- Modify: `docs/ai/services/organization.md`
- Modify: `docs/ai/services/patient.md`

- [ ] **Step 1: Cập nhật DDL Organization**

Đổi ba `CREATE TABLE` và toàn bộ constraint/index đi kèm sang `DEPARTMENT`, `STAFF`, `ACCOUNT`. Dùng chính xác tên cột trong Task 2; hai khóa ngoại vòng tròn là:

```sql
STAFF.department_id REFERENCES DEPARTMENT(department_id)
DEPARTMENT.department_head_id REFERENCES STAFF(staff_id)
```

Khóa ngoại tài khoản:

```sql
ACCOUNT.staff_id REFERENCES STAFF(staff_id)
```

- [ ] **Step 2: Cập nhật các tham chiếu persistence Organization**

Trong đặc tả, thay tên bảng/cột cũ ở business rule, port mô tả, điểm dễ sai và test description bằng thuật ngữ tiếng Anh. Không đổi routing key và không bắt buộc đổi tên lớp Java trong kế hoạch tài liệu này.

- [ ] **Step 3: Cập nhật DDL Patient**

Đổi `CREATE TABLE BENH_NHAN` thành `CREATE TABLE PATIENT` và dùng đúng các cột:

```sql
patient_id UUID PRIMARY KEY
full_name VARCHAR(100) NOT NULL
date_of_birth DATE NOT NULL
gender VARCHAR(10) NOT NULL
identity_number VARCHAR(20) NOT NULL UNIQUE
address VARCHAR(255)
phone_number VARCHAR(15)
email VARCHAR(100)
health_insurance_number VARCHAR(20)
created_at TIMESTAMPTZ NOT NULL DEFAULT now()
updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
```

- [ ] **Step 4: Cập nhật tham chiếu persistence Patient**

Thay các tên cột cũ trong business rule và phần điểm dễ sai bằng `identity_number`, `health_insurance_number`, `date_of_birth`, `phone_number` tương ứng. Không dịch nội dung mô tả nghiệp vụ.

- [ ] **Step 5: Kiểm tra hai tài liệu `docs/ai/services`**

Run:

```powershell
rg -n 'KHOA|NHAN_VIEN|TAI_KHOAN|BENH_NHAN|ma_khoa|ma_benh_nhan|ho_ten|ngay_sinh|so_cmnd|bhyt_so' docs/ai/services/organization.md docs/ai/services/patient.md
```

Expected: không có kết quả tên kỹ thuật cũ. Nếu có, thay bằng ánh xạ trong đặc tả thiết kế.

- [ ] **Step 6: Commit Organization và Patient specs**

```powershell
git add -- docs/eproject_general_plan/backend-spec/01-organization.md docs/eproject_general_plan/backend-spec/02-patient.md docs/ai/services/organization.md docs/ai/services/patient.md
git commit -m "docs: align organization and patient schemas in english"
```

---

### Task 4: Đồng bộ đặc tả Notification

**Files:**

- Modify: `docs/eproject_general_plan/backend-spec/07-notification.md`
- Modify: `docs/ai/services/notification.md`

- [ ] **Step 1: Cập nhật DDL Notification**

Dùng schema chính xác:

```sql
CREATE TABLE NOTIFICATION (
    notification_id    UUID          PRIMARY KEY,
    patient_id         UUID          NOT NULL,
    title              VARCHAR(255)  NOT NULL,
    content            TEXT          NOT NULL,
    channel            VARCHAR(10)   NOT NULL,
    recipient_address  VARCHAR(150),
    status             VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    failure_reason     VARCHAR(255),
    retry_count        INT           NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    sent_at            TIMESTAMPTZ
);

CREATE INDEX idx_notification_patient
    ON NOTIFICATION (patient_id, created_at DESC);
CREATE INDEX idx_notification_status
    ON NOTIFICATION (status);

CREATE TABLE PROCESSED_EVENT (
    event_id      UUID          PRIMARY KEY,
    routing_key   VARCHAR(100)  NOT NULL,
    processed_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
```

- [ ] **Step 2: Cập nhật bảng ánh xạ persistence Notification**

Dùng các cặp DB–Java/DTO hiện hành nhưng đổi phía DB thành:

```text
notification_id, patient_id, title, content, channel,
recipient_address, status, failure_reason, retry_count, created_at, sent_at
```

Chỉ sửa tên Java/DTO khi tài liệu đã có một tên tiếng Anh tương ứng; không phát minh thay đổi API ngoài phạm vi.

- [ ] **Step 3: Cập nhật tài liệu service Notification**

Thay phần đầu thành:

```text
DB tables: NOTIFICATION, PROCESSED_EVENT
```

Mô tả đầy đủ các cột giống DDL ở Step 1 và sửa flow thành “create `NOTIFICATION` (PENDING)”.

- [ ] **Step 4: Kiểm tra tên kỹ thuật cũ**

Run:

```powershell
rg -n 'THONG_BAO|SU_KIEN_DA_XU_LY|ma_thong_bao|ma_benh_nhan|tieu_de|noi_dung|dia_chi_nhan|trang_thai|ly_do_that_bai|so_lan_thu|ngay_tao|ngay_gui|xu_ly_luc' docs/eproject_general_plan/backend-spec/07-notification.md docs/ai/services/notification.md
```

Expected: không có kết quả tên kỹ thuật cũ, ngoại trừ bảng ánh xạ lịch sử nếu được giữ có chủ đích; trường hợp đó phải có nhãn rõ “Tên cũ”.

- [ ] **Step 5: Commit Notification specs**

```powershell
git add -- docs/eproject_general_plan/backend-spec/07-notification.md docs/ai/services/notification.md
git commit -m "docs: align notification schema in english"
```

---

### Task 5: Render lại bốn sơ đồ Word-safe

**Files:**

- Regenerate: `docs/monitor_proj_progress/02-giai-doan-2-thiet-ke-giao-dien-tieu-chuan/assets/diagrams/svg/*.svg`
- Regenerate: `docs/monitor_proj_progress/02-giai-doan-2-thiet-ke-giao-dien-tieu-chuan/assets/diagrams/word-svg/*.svg`
- Regenerate: `docs/monitor_proj_progress/02-giai-doan-2-thiet-ke-giao-dien-tieu-chuan/assets/diagrams/png/*.png`

- [ ] **Step 1: Render raw SVG bằng Pretty Mermaid**

Render đúng bốn basename bằng palette đang dùng:

```text
bg=#FFFFFF
fg=#0F172A
line=#2563EB
accent=#0F766E
muted=#64748B
surface=#F0FDFA
border=#0F766E
font=Arial
padding=24
```

Run `node C:/Users/VIP/.codex/skills/pretty-mermaid/scripts/render.mjs` cho từng `.mmd`, xuất vào thư mục `svg` tương ứng.

Expected: bốn lần xuất hiện thông báo `SVG diagram saved` và exit code 0.

- [ ] **Step 2: Tạo SVG tự chứa và PNG**

Dùng `prepareSvgForPng` của Pretty Mermaid, loại bỏ `@import` font ngoài, đổi `Segoe UI` thành `Arial`, sau đó render PNG bằng `@resvg/resvg-js` với:

```text
width=2400
background=#FFFFFF
fontFiles=arial.ttf, arialbd.ttf, consola.ttf, consolab.ttf
loadSystemFonts=false
defaultFontFamily=Arial
```

Expected: bốn Word-safe SVG và bốn PNG được ghi thành công.

- [ ] **Step 3: Kiểm tra trực quan**

Mở cả bốn PNG và xác nhận:

- Tên bảng/cột không bị cắt.
- Không còn tên kỹ thuật tiếng Việt.
- Cardinality và hướng mũi tên đúng nguồn Mermaid.
- Chữ Arial rõ khi thu nhỏ trên trang Word.

- [ ] **Step 4: Commit tài sản render**

```powershell
git add -- docs/monitor_proj_progress/02-giai-doan-2-thiet-ke-giao-dien-tieu-chuan/assets/diagrams
git commit -m "docs: regenerate english database diagrams"
```

---

### Task 6: Kiểm tra chéo toàn bộ phạm vi

**Files:**

- Verify: toàn bộ file được liệt kê trong File map.

- [ ] **Step 1: Kiểm tra mỗi Mermaid source có đủ ba output**

Với bốn basename, xác nhận tồn tại và khác rỗng:

```text
src/<name>.mmd
svg/<name>.svg
word-svg/<name>.svg
png/<name>.png
```

- [ ] **Step 2: Kiểm tra định dạng Word-safe**

Xác nhận từng Word-safe SVG bắt đầu bằng `<svg` và không chứa:

```text
@import
fonts.googleapis.com
var(
color-mix(
```

Xác nhận từng PNG có chữ ký PNG hợp lệ và chiều rộng 2400 px.

- [ ] **Step 3: Kiểm tra nhất quán tên schema**

Run:

```powershell
rg -n 'KHOA|NHAN_VIEN|TAI_KHOAN|BENH_NHAN|THONG_BAO|SU_KIEN_DA_XU_LY' docs/monitor_proj_progress/02-giai-doan-2-thiet-ke-giao-dien-tieu-chuan docs/ai/services/organization.md docs/ai/services/patient.md docs/ai/services/notification.md docs/eproject_general_plan/backend-spec/01-organization.md docs/eproject_general_plan/backend-spec/02-patient.md docs/eproject_general_plan/backend-spec/07-notification.md
```

Expected: không còn tên kỹ thuật cũ trong tài liệu kết quả; nếu một bảng ánh xạ lịch sử giữ tên cũ, kết quả phải nằm trong cột “Tên cũ” và không được dùng như schema hiện hành.

- [ ] **Step 4: Kiểm tra liên kết Markdown**

Phân giải mọi liên kết tương đối trong báo cáo Giai đoạn 2 và hai README sơ đồ. Expected: không có đường dẫn bị thiếu.

- [ ] **Step 5: Kiểm tra diff**

Run:

```powershell
git diff --check
git status --short
```

Expected: `git diff --check` exit code 0; `git status` chỉ liệt kê các thay đổi thuộc phạm vi hoặc thay đổi có sẵn của người dùng được giữ nguyên.

- [ ] **Step 6: Commit kiểm tra cuối nếu còn chỉnh sửa nhỏ**

```powershell
git add -- docs/monitor_proj_progress/02-giai-doan-2-thiet-ke-giao-dien-tieu-chuan docs/ai/services/organization.md docs/ai/services/patient.md docs/ai/services/notification.md docs/eproject_general_plan/backend-spec/01-organization.md docs/eproject_general_plan/backend-spec/02-patient.md docs/eproject_general_plan/backend-spec/07-notification.md
git commit -m "docs: finalize english database terminology"
```
