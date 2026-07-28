# Service: organization

**Module:** `backend/organization-service/` · **Base path:** `/api/v1/org` · **DB tables:** `KHOA`, `NHAN_VIEN`, `TAI_KHOAN`

**Source of truth:** `docs/eproject_general_plan/organization-service.html`

> **This service is the foundation the rest of the system references.** It owns the three things
> everything else points at:
>
> 1. **Departments** — `KHOA`, and the `ma_khoa` that every operational table and every domain event
>    carries. Without it there is no departmental dimension at all.
> 2. **Staff** — `NHAN_VIEN` is the `ma_bac_si` that appears in `LICH_HEN`, `HO_SO_BA` and `BAN_KE_CP`.
> 3. **Accounts** — `TAI_KHOAN` is what the gateway checks before issuing a JWT.

## Bounded context

Owns: the hospital's **organisational structure** — which departments exist, who works in them, and
how those people authenticate.

Does NOT own: patients, appointments, records, tests, drugs, money. It answers *who* and *where*,
never *what happened to a patient*.

## Data

**`KHOA`** — a department (khoa/phòng)
`ma_khoa` UUID PK · `ten_khoa` VARCHAR(100) · `ma_viet_tat` VARCHAR(20) UNIQUE · `loai_khoa` ENUM('LAM_SANG','CAN_LAM_SANG','HANH_CHINH') · `truong_khoa` UUID (ref `NHAN_VIEN`, nullable) · `dia_diem` VARCHAR(255) · `hoat_dong` BOOLEAN · `created_at` · `updated_at`.

**`NHAN_VIEN`** — a staff member (doctor, nurse, technician, pharmacist, cashier…)
`ma_nhan_vien` UUID PK · `ho_ten` VARCHAR(100) · `ma_khoa` UUID (FK, same service) · `chuc_danh` ENUM('BAC_SI','DIEU_DUONG','KY_THUAT_VIEN','DUOC_SI','THU_NGAN','QUAN_LY','HANH_CHINH') · `chuyen_khoa` VARCHAR(100) NULL · `so_chung_chi` VARCHAR(50) NULL · `so_dien_thoai` VARCHAR(15) · `email` VARCHAR(100) · `trang_thai` ENUM('DANG_LAM','NGHI_VIEC') · `created_at` · `updated_at`.

**`TAI_KHOAN`** — a login
`ma_tai_khoan` UUID PK · `ten_dang_nhap` VARCHAR(50) UNIQUE · `mat_khau_hash` VARCHAR(255) · `ma_nhan_vien` UUID (FK, same service, nullable) · `vai_tro` ENUM('ADMIN','DOCTOR','NURSE','PHARMACIST','CASHIER','LAB_TECH','MANAGER','PATIENT','SYSTEM') · `kich_hoat` BOOLEAN · `lan_dang_nhap_cuoi` TIMESTAMPTZ NULL · `created_at` · `updated_at`.

`vai_tro` mirrors `backend/common/security/Roles.java` exactly — keep them in sync.

## Endpoints

| Method | Path | Roles |
|--------|------|-------|
| GET | `/api/v1/org/departments` | ADMIN, MANAGER, DOCTOR, NURSE |
| GET | `/api/v1/org/departments/{id}` | ADMIN, MANAGER, DOCTOR, NURSE |
| POST | `/api/v1/org/departments` | ADMIN |
| PUT | `/api/v1/org/departments/{id}` | ADMIN |
| GET | `/api/v1/org/staff?maKhoa&chucDanh&page&size` | ADMIN, MANAGER, DOCTOR, NURSE |
| GET | `/api/v1/org/staff/{id}` | ADMIN, MANAGER, DOCTOR, NURSE |
| POST | `/api/v1/org/staff` | ADMIN |
| PUT | `/api/v1/org/staff/{id}` | ADMIN |
| PUT | `/api/v1/org/staff/{id}/department` `{maKhoa}` | ADMIN |
| GET | `/api/v1/org/staff/{id}/exists` | SYSTEM *(internal lookup used by other services)* |
| POST | `/api/v1/org/accounts` | ADMIN |
| PUT | `/api/v1/org/accounts/{id}/status` `{kichHoat}` | ADMIN |
| POST | `/api/v1/org/accounts/verify` `{tenDangNhap, matKhau}` | SYSTEM *(gateway only — never exposed publicly)* |

`/accounts/verify` is what turns the gateway's stub login into real authentication: the gateway posts
credentials, this service checks the hash and returns `{maTaiKhoan, maNhanVien, maKhoa, vaiTro}`, and
the gateway mints the JWT. **The gateway never reads `TAI_KHOAN` directly** — that would be
cross-service DB access.

## Events

- **Publish:**
  - `department.created` `{maKhoa, tenKhoa, loaiKhoa}`
  - `staff.created` `{maNhanVien, hoTen, maKhoa, chucDanh}`
  - `staff.department.changed` `{maNhanVien, maKhoaCu, maKhoaMoi}` — a transfer between departments
- **Subscribe:** none. This is reference data; it drives other contexts rather than reacting to them.

## Business rules

1. `ten_dang_nhap` is unique; passwords stored as a **BCrypt hash**, never plaintext, never logged.
2. A `NHAN_VIEN` must belong to exactly one active `KHOA`.
3. `chuc_danh = BAC_SI` requires a non-empty `so_chung_chi` (practising certificate).
4. `truong_khoa`, if set, must be a `NHAN_VIEN` **of that same department**.
5. A department with active staff cannot be set `hoat_dong = false`.
6. Transferring staff (`/department`) publishes `staff.department.changed`; it never deletes and recreates.
7. `vai_tro = PATIENT` accounts have no `ma_nhan_vien` (patients are not staff).
8. Deactivating an account (`kich_hoat = false`) must invalidate future logins; existing JWTs expire naturally.

## Why this service makes the topic real

Once `ma_khoa` exists, every other service can carry it, and questions that define a *departmental*
system become answerable:

| Question | Needs |
|----------|-------|
| Which department ordered this lab test? | `XET_NGHIEM.ma_khoa_chi_dinh` |
| Revenue per department this month | `VIEN_PHI.ma_khoa` + report aggregation |
| Which doctors are in Khoa Nội, and who leads it? | `NHAN_VIEN.ma_khoa`, `KHOA.truong_khoa` |
| Move a doctor from Khoa Nội to Khoa Ngoại | `staff.department.changed` |
| Can this user perform this action? | `TAI_KHOAN.vai_tro`, resolved at login |

Before this service, none of them could be answered.

## Cross-service

- Other services hold `ma_khoa` / `ma_bac_si` as **bare UUIDs** — no JPA relation, no cross-DB join (`08`).
- `appointment` and `medical-record` REST-check staff existence via `/api/v1/org/staff/{id}/exists`, resilient (timeout + circuit breaker + fallback), exactly as they do for patients (`01`).
- The gateway calls `/api/v1/org/accounts/verify` at login.
