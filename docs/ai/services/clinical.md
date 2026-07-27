# Service: clinical (Khoa Khám bệnh)

**Source of truth:** `EProject/clinical-service.html`
**Module:** `clinical-service/` · **Base paths:** `/api/v1/appointments`, `/api/v1/records` · **DB tables:** `LICH_HEN`, `HO_SO_BA`, `CHUAN_DOAN`

> **Why appointments and records share one service.** They are one department's single workflow —
> book, examine, record. Separating them would force `HO_SO_BA.ma_lich_hen` to become a foreign key
> across a network boundary, and would require publishing an event just to set the appointment to
> `DA_DEN` — a message broker doing the work of **one local transaction**. Together, neither problem
> exists.

## Bounded context

Owns: the outpatient examination workflow — appointments, medical records, diagnoses.

Does NOT own: patients (→ `patient`), staff and departments (→ `organization`), lab tests (→ `lab`),
drugs (→ `pharmacy`), money (→ `billing`).

## Data

**`LICH_HEN`** — appointment
`ma_lich_hen` UUID PK · `ma_benh_nhan` UUID (ref → patient) · `ma_bac_si` UUID (ref → organization `NHAN_VIEN`) · `ma_khoa` UUID (ref → organization `KHOA`) · `ngay_hen` DATE · `gio_hen` TIME · `trang_thai` ENUM('CHUA_DEN','DA_DEN','HUY') · `ly_do_kham` TEXT · `created_at` · `updated_at`.

**`HO_SO_BA`** — medical record
`ma_ho_so` UUID PK · `ma_benh_nhan` UUID (ref → patient) · `ma_bac_si` UUID (ref → organization) · `ma_khoa` UUID (ref → organization) · `ngay_kham` DATE · `trieu_chung` TEXT · `ma_lich_hen` UUID (**FK, same service**, nullable) · `created_at` · `updated_at`.

**`CHUAN_DOAN`** — diagnosis
`ma_chuan_doan` UUID PK · `ma_ho_so` UUID (FK, same service) · `ten_chuan_doan` VARCHAR(255) · `mo_ta` TEXT · `icd_code` VARCHAR(10).

`ma_khoa` is what makes this a *departmental* system: it records which department the appointment and
examination belong to, so every downstream fee, test and report can be attributed to a department.

## Endpoints

| Method | Path | Roles |
|--------|------|-------|
| GET | `/api/v1/appointments/{id}` | ADMIN, DOCTOR, NURSE |
| GET | `/api/v1/appointments/patient/{patientId}` | ADMIN, DOCTOR, NURSE |
| GET | `/api/v1/appointments?maKhoa&ngayHen&page&size` | ADMIN, MANAGER, DOCTOR, NURSE |
| POST | `/api/v1/appointments` | ADMIN, NURSE |
| PUT | `/api/v1/appointments/{id}` | ADMIN, DOCTOR, NURSE |
| PUT | `/api/v1/appointments/{id}/status` `{trangThai}` | ADMIN, DOCTOR, NURSE |
| GET | `/api/v1/records/{id}` | ADMIN, DOCTOR, NURSE |
| GET | `/api/v1/records/patient/{patientId}` | ADMIN, DOCTOR, NURSE |
| POST | `/api/v1/records` | ADMIN, DOCTOR |
| PUT | `/api/v1/records/{id}` | ADMIN, DOCTOR |
| POST | `/api/v1/records/{id}/diagnoses` | ADMIN, DOCTOR |

## Events

- **Publish:**
  - `appointment.created` `{appointmentId, patientId, doctorId, maKhoa, ngayHen, gioHen}`
  - `appointment.status.changed` `{appointmentId, status, patientId, maKhoa}`
  - `medicalrecord.created` `{recordId, patientId, doctorId, maKhoa, diagnosis, ngayKham}`
  - `diagnosis.added` `{recordId, diagnosisCode, diagnosisName}`
- **Subscribe:**
  - `lab.result.created` → attach the result to the record
  - `prescription.filled` → attach prescription info to the record

> Creating a record for an appointment flips that appointment to `DA_DEN` **in the same
> transaction** — never via an event. Publishing an event to update your own database means the
> service boundary is cut in the wrong place.

## Business rules

**Appointments**
1. Cannot create an appointment for a past date.
2. A patient cannot hold more than one `CHUA_DEN` appointment on the same day.
3. `gio_hen` must fall within 07:00–17:00.
4. The doctor must exist and belong to the department in `ma_khoa` (REST-check `organization-service`).

**Records**
5. Every record must have at least one diagnosis.
6. A patient has one active record per examination; create one if none exists.
7. Cannot create a record if the patient does not exist (REST-check `patient-service`).
8. Creating a record from an appointment sets that appointment to `DA_DEN` — **same transaction**, not an event.

## Cross-service

Both are synchronous reads, so both must be resilient (timeout + circuit breaker + fallback, `01`):

- `patient-service` — does this patient exist?
- `organization-service` — does this doctor exist, and are they in this department?
