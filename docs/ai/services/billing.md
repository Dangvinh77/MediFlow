# Service: billing

**Source of truth:** `docs/eproject_general_plan/billing-service.html`
**Module:** `backend/billing-service/` · **Base path:** `/api/v1/billing` · **DB tables:** `VIEN_PHI`, `HOADON`

## Bounded context
Owns: fees (`VIEN_PHI`), invoices (`HOADON`). **Saga orchestrator** for prescribe → dispense → pay. Does NOT own: patients, drugs, records.

## Data
`VIEN_PHI`: `ma_vien_phi` UUID PK · `ma_benh_nhan` UUID (ref → patient) · `ma_ho_so` UUID (ref → clinical, nullable) · `ma_khoa` UUID (ref → organization `KHOA`, **which department the charge arose in**) · `ngay_phat_sinh` DATE · `loai_phi` ENUM('KHAM','XN','THUOC','DV') · `so_tien` DECIMAL(15,2) · `da_thanh_toan` BOOLEAN.

`ma_khoa` is what makes revenue-per-department reportable. Every fee is attributed to the department
that generated it, taken from the `maKhoa` on the event that triggered it.
`HOADON`: `ma_hoa_don` UUID PK · `ma_benh_nhan` UUID (ref) · `ngay_tao` DATE · `tong_tien` DECIMAL(15,2) · `da_thanh_toan` BOOLEAN · `hinh_thuc_tt` ENUM('TIEN_MAT','CHUYEN_KHOAN','BHYT') · `ma_phieu_xuat` UUID (ref pharmacy).

## Endpoints
| Method | Path | Roles |
|--------|------|-------|
| GET | `/api/v1/billing/invoices/{id}` | ADMIN, CASHIER |
| GET | `/api/v1/billing/patient/{patientId}` | ADMIN, CASHIER |
| POST | `/api/v1/billing/invoices` | ADMIN, CASHIER |
| PUT | `/api/v1/billing/invoices/{id}/pay` | ADMIN, CASHIER |

## Events
- **Publish:** `invoice.created` `{invoiceId, patientId, totalAmount, items}`; `payment.completed` `{invoiceId, patientId, totalAmount, paymentMethod}`; `payment.failed` `{invoiceId, patientId, reason}`.
- **Subscribe:** `prescription.created` (pharmacy) → create invoice; `lab.result.created` (lab) → create fee; `medicalrecord.created` → create exam fee; `appointment.status.changed` → create fee if `DA_DEN`.

## Business rules
1. Cannot pay an already-paid invoice.
2. `tong_tien` = Σ(`so_tien`) of the related unpaid `VIEN_PHI`.
3. On successful payment, set all related `VIEN_PHI.da_thanh_toan = true`.
4. **Saga:** if a downstream step (dispense) fails, publish `payment.failed` to trigger rollback/compensation.

## Saga orchestration (this service drives it)
`prescription.created` → create invoice → on `/pay` success → `payment.completed` → pharmacy dispenses.
Failure anywhere → `payment.failed` → notification + compensation. Consumers must be idempotent (dedupe on eventId).
