# Service: patient

**Source of truth:** `EProject/patient-service.html`
**Module:** `patient-service/` · **Base path:** `/api/v1/patients` · **DB table:** `BENH_NHAN`

## Bounded context
Owns: patient demographics & records incl. BHYT. Does NOT own: appointments, medical records, billing, drugs.

## Data — `BENH_NHAN`
`ma_benh_nhan` UUID PK · `ho_ten` VARCHAR(100) · `ngay_sinh` DATE · `gioi_tinh` ENUM('M','F') · `so_cmnd` VARCHAR(20) UNIQUE · `dia_chi` VARCHAR(255) · `so_dien_thoai` VARCHAR(15) · `email` VARCHAR(100) · `bhyt_so` VARCHAR(20) NULL · `created_at` · `updated_at`.

## Endpoints
| Method | Path | Roles |
|--------|------|-------|
| GET | `/api/v1/patients/{id}` | ADMIN, DOCTOR, NURSE |
| GET | `/api/v1/patients?page&size&keyword` | ADMIN, DOCTOR, NURSE |
| POST | `/api/v1/patients` (CreatePatientRequest) | ADMIN, NURSE |
| PUT | `/api/v1/patients/{id}` (UpdatePatientRequest) | ADMIN, NURSE |
| DELETE | `/api/v1/patients/{id}` → 204 | ADMIN |

DTO fields: `maBenhNhan, hoTen, ngaySinh, gioiTinh, soCmnd, diaChi, soDienThoai, email, bhytSo, createdAt, updatedAt`.

## Events
- **Publish:** `patient.created` `{patientId, hoTen, email, sdt}`; `patient.updated` `{patientId, hoTen, email, sdt, diaChi}`.
- **Subscribe:** `payment.completed` (from billing) → log only.

## Business rules
1. `so_cmnd` unique.
2. `email` valid format (regex).
3. `bhyt_so` (if present) matches `XX-XXXXXXXX-X`.
4. `ngay_sinh` not in the future.
5. `so_dien_thoai` digits only, ≥ 10.
