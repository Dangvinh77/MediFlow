# Service: pharmacy

**Source of truth:** `EProject/pharmacy-service.html`
**Module:** `backend/pharmacy-service/` · **Base path:** `/api/v1/pharmacy` · **DB tables:** `THUOC`, `BAN_KE_CP`, `CHI_TIET_BAN_KE`, `PHIEU_XUAT`

## Bounded context
Owns: drugs, prescriptions, dispense slips, stock. Does NOT own: billing, records. Saga participant.

## Data
`THUOC`: `ma_thuoc` UUID PK · `ten_thuoc` · `hoat_chat` · `don_vi_tinh` · `gia` DECIMAL(15,2) · `so_luong_ton` INT · `han_su_dung` DATE · `nha_san_xuat`.
`BAN_KE_CP` (prescription): `ma_ban_ke` UUID PK · `ma_ho_so` UUID (ref → clinical) · `ma_bac_si` UUID (ref → organization `NHAN_VIEN`) · `ma_khoa` UUID (ref → organization `KHOA`, the prescribing department) · `ngay_ke` DATE · `tong_tien` DECIMAL(15,2).
`CHI_TIET_BAN_KE` (lines) & `PHIEU_XUAT` (dispense slip, status `CHO_XUAT`/`DA_XUAT`) — per design doc.

## Endpoints
| Method | Path | Roles |
|--------|------|-------|
| GET | `/api/v1/pharmacy/drugs?page&keyword` | ADMIN, DOCTOR, PHARMACIST |
| GET | `/api/v1/pharmacy/drugs/{id}` | ADMIN, DOCTOR, PHARMACIST |
| POST | `/api/v1/pharmacy/drugs` | ADMIN, PHARMACIST |
| PUT | `/api/v1/pharmacy/drugs/{id}/stock` | ADMIN, PHARMACIST |
| POST | `/api/v1/pharmacy/prescriptions` | ADMIN, DOCTOR |
| PUT | `/api/v1/pharmacy/prescriptions/{id}/dispense` | ADMIN, PHARMACIST |

## Events
- **Publish:** `prescription.created` `{prescriptionId, patientId, recordId, totalAmount, items}`; `prescription.filled` `{prescriptionId, patientId, totalAmount, dispensedItems}`; `stock.low` `{drugId, drugName, currentStock}`.
- **Subscribe:** `payment.completed` (billing) → move dispense slip `CHO_XUAT` → `DA_XUAT`.

## Business rules
1. Do not dispense if `so_luong_ton` < requested quantity.
2. Do not dispense if `han_su_dung` < today.
3. Creating a prescription auto-creates a dispense slip with status `CHO_XUAT`.
4. Dispensing decrements stock.
5. Prescription total = Σ(quantity × unit price).
6. **Saga:** if dispense fails (stock/expiry), publish a failure event so billing compensates (`payment.failed`).
