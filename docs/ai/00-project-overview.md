# 00 — Project Overview

## What we are building

**MediFlow** — a **hospital / clinic management system** built as **Spring Boot microservices**. The authoritative technical design lives in `docs/eproject_general_plan/*.html` (one file per service). These AI rules turn that design into consistent, buildable code.

## The 9 services

| Service | Bounded context (owns) | Key tables |
|---------|------------------------|------------|
| **gateway** | API gateway: JWT auth, RBAC, routing, rate limiting. No business data. | route config only |
| **patient** | Patient demographics & records (incl. BHYT) | `BENH_NHAN` |
| **appointment** | Appointments, status, scheduling | `LICH_HEN` |
| **medical-record** | Medical records & diagnoses | `HO_SO_BA`, `CHUAN_DOAN` |
| **lab** | Lab tests & results | `XET_NGHIEM`, `KET_QUA_XN` |
| **pharmacy** | Drugs, prescriptions, dispensing, stock | `THUOC`, `BAN_KE_CP`, `PHIEU_XUAT`, `CHI_TIET_BAN_KE` |
| **billing** | Fees, invoices, **Saga orchestrator** (prescribe→dispense→pay) | `VIEN_PHI`, `HOADON` |
| **notification** | Email/SMS/in-app notification history | `THONG_BAO` |
| **report** | Aggregated analytics (read-model built from events) | `DAILY_VISIT_REPORT`, `MONTHLY_REVENUE_REPORT`, `DRUG_STATISTIC` |

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

## Roles in the system

`ADMIN`, `DOCTOR`, `NURSE`, `PHARMACIST`, `CASHIER`, `LAB_TECH`, `MANAGER`, `PATIENT`, `SYSTEM`.
Every endpoint declares which roles may call it — see `07-security-rbac.md`.
