# Service: lab

**Source of truth:** `docs/eproject_general_plan/lab-service.html`
**Module:** `backend/lab-service/` · **Base path:** `/api/v1/lab` · **DB tables:** `XET_NGHIEM`, `KET_QUA_XN`

## Bounded context
Owns: lab tests (`XET_NGHIEM`) & results (`KET_QUA_XN`). Does NOT own: patients, records, billing.

## Data
`XET_NGHIEM`: `ma_xn` UUID PK · `ma_ho_so` UUID (ref → clinical) · `ma_benh_nhan` UUID (ref → patient) · `ma_khoa_chi_dinh` UUID (ref → organization `KHOA`, which department ordered it) · `loai_xn` VARCHAR(50) · `ngay_yeu_cau` DATE · `ngay_thuc_hien` DATE · `trang_thai` ENUM('CHO','DANG','HOAN_THANH','HUY') · `ket_luan` TEXT.
`KET_QUA_XN`: `ma_ket_qua` UUID PK · `ma_xn` UUID (FK, same service) · `chi_so` VARCHAR(100) · `gia_tri` VARCHAR(50) · `don_vi` VARCHAR(20) · `chi_so_binh_thuong` VARCHAR(50).

## Endpoints
| Method | Path | Roles |
|--------|------|-------|
| GET | `/api/v1/lab/{id}` | ADMIN, DOCTOR, NURSE |
| GET | `/api/v1/lab/patient/{patientId}` | ADMIN, DOCTOR |
| POST | `/api/v1/lab` | ADMIN, DOCTOR |
| PUT | `/api/v1/lab/{id}/results` | ADMIN, LAB_TECH |
| PUT | `/api/v1/lab/{id}/status` | ADMIN, LAB_TECH |

## Events
- **Publish:** `lab.request.created` `{labId, patientId, recordId, loaiXn, ngayYeuCau}`; `lab.result.created` `{labId, patientId, recordId, ketQua, ketLuan}`.
- **Subscribe:** `medicalrecord.created` → auto-create sample lab test if indicated; `payment.completed` → update payment status for the test.

## Business rules
1. Cannot add a result if `trang_thai` is `HOAN_THANH` or `HUY`.
2. Adding a result auto-transitions `trang_thai` → `HOAN_THANH`.
3. `ngay_thuc_hien` ≥ `ngay_yeu_cau`.
