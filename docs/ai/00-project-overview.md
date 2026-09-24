# 00 — Project Overview

## What we are building

**MediFlow** — a **hospital / clinic management system** built as **Spring Boot microservices**. The authoritative technical design lives in `docs/eproject_general_plan/*.html` (one file per service). These AI rules turn that design into consistent, buildable code.

## The service landscape

MediFlow has ten business bounded contexts in the approved target architecture. Eight have
implemented business services. Inpatient now has a preliminary bootable foundation (module,
configuration, database and Compose service) but remains **planned** until its business spec,
endpoints, schema, contracts and Gateway route are implemented. Surgery remains planned and
unscaffolded.

| Service | Bounded context (owns) | Key tables |
|---------|------------------------|------------|
| **gateway** | API gateway: JWT auth, RBAC, routing, rate limiting. No business data. | route config only |
| **organization** | Departments, staff, accounts and authoritative staff/department identities | `DEPARTMENT`, `STAFF`, `ACCOUNT` |
| **patient** | Patient demographics & records (incl. BHYT) | `BENH_NHAN` |
| **clinical** | Outpatient appointments, medical records, diagnoses and admission referrals | `APPOINTMENT`, `MEDICAL_RECORD`, `DIAGNOSIS` |
| **lab** | Lab tests & results | `XET_NGHIEM`, `KET_QUA_XN` |
| **pharmacy** | Drugs, prescriptions, dispensing, stock | `THUOC`, `BAN_KE_CP`, `PHIEU_XUAT`, `CHI_TIET_BAN_KE` |
| **billing** | Charges, payment requests/transactions, allocations, deposits, refunds and settlement | current `VIEN_PHI`, `HOADON`; target ledger tables |
| **notification** | Email/SMS/in-app notification history | `THONG_BAO` |
| **report** | Aggregated analytics (read-model built from events) | `DAILY_VISIT_REPORT`, `MONTHLY_REVENUE_REPORT`, `DRUG_STATISTIC` |
| **inpatient** *(planned)* | Admissions, beds, assignments, treatment log and discharge | target `ADMISSION`, `BED`, `BED_ASSIGNMENT`, `TREATMENT_ENTRY`, `DISCHARGE_SUMMARY` |
| **surgery** *(planned)* | Surgery case, consent, pre-op checklist, schedule, team and result | target `SURGERY_CASE`, `CONSENT`, `PREOP_CHECK_ITEM`, `SURGERY_SCHEDULE`, `SURGERY_RESULT` |

Supporting infrastructure: **Eureka** (service registry), **RabbitMQ** (event bus), a **config source** for gateway routes.

## Bilingual naming (IMPORTANT)

The domain is expressed in **Vietnamese**. We keep it in the database and preserve it in a controlled way:

- **Database tables/columns:** Vietnamese `snake_case` — `BENH_NHAN`, `ma_benh_nhan`, `ho_ten`.
- **Java fields, DTOs, JSON:** Vietnamese `camelCase` — `maBenhNhan`, `hoTen`, `ngaySinh`.
- **Class names / packages / URLs:** **English** — `Patient`, `PatientController`, `/api/v1/patients`.

See `08-persistence-naming.md` for the exact mapping mechanism.

## Glossary (VN → EN, for class/URL naming)

| Vietnamese | English | Used in |
|-----------|---------|---------|
| khoa | department | organization-service |
| nhan_vien | staff | organization-service |
| tai_khoan | account | organization-service |
| benh_nhan | patient | patient-service |
| lich_hen | appointment | clinical-service |
| ho_so (benh an) | medical record | clinical-service |
| chuan_doan | diagnosis | clinical-service |
| xet_nghiem | lab test | lab-service |
| ket_qua_xn | lab result | lab-service |
| thuoc | drug | pharmacy-service |
| ban_ke (cp) | prescription | pharmacy-service |
| phieu_xuat | dispense slip | pharmacy-service |
| vien_phi | fee | billing-service |
| hoa_don | invoice | billing-service |
| thong_bao | notification | notification-service |
| bao_cao | report | report-service |
| nhap_vien | admission | inpatient-service *(planned)* |
| giuong_benh | bed | inpatient-service *(planned)* |
| phau_thuat | surgery | surgery-service *(planned)* |
| tam_ung | deposit | billing-service |
| quyet_toan | settlement | billing-service |

## Roles in the system

`ADMIN`, `DOCTOR`, `NURSE`, `PHARMACIST`, `CASHIER`, `LAB_TECH`, `MANAGER`, `PATIENT`, `SYSTEM`.
Every endpoint declares which roles may call it — see `07-security-rbac.md`.
