# Frontend Context Guides — Outpatient MVP

Web client for MediFlow: A Microservices-Based Hospital Information System for Outpatient Care Management. The MVP targets multi-specialty clinics and hospital outpatient departments. Organization/Patient run inside Core; Report/Notification remain separate pending Support. Guides follow context ownership, not the count of deployment applications.

Read the guide for the service you are changing after the global
[`frontend/AGENTS.md`](../../AGENTS.md),
[`12-frontend.md`](../../../docs/ai/12-frontend.md), and
[`15-frontend-ownership.md`](../../../docs/ai/15-frontend-ownership.md).

These guides contain the effective writable paths, current UI baseline, owner queue, and known
handoffs. Backend controllers and DTOs remain the wire-contract authority.

Before editing a context, read both `src/features/<context>/AGENTS.md` and its matching
`src/app/(dashboard)/<route>/AGENTS.md`. These files state the effective owner and writable paths;
this service index does not grant additional authority.

| Service | Owner | Current frontend baseline | Guide |
|---|---|---|---|
| Clinical | Vinh (`Dangvinh77` / `Harori`) | Appointment and medical-record workflows | [clinical.md](clinical.md) |
| Lab | Vinh (`Dangvinh77` / `Harori`) | Queue, detail, start, result and cancel | [lab.md](lab.md) |
| Inpatient | Vinh (`Dangvinh77` / `Harori`) | Preserved read workspace; deferred from outpatient MVP | [inpatient.md](inpatient.md) |
| Pharmacy | Huy (`LQHuy0210`) | Drug and prescription workflows | [pharmacy.md](pharmacy.md) |
| Report | Huy (`LQHuy0210`) | Daily, monthly and top-medicine reports | [report.md](report.md) |
| Surgery | Huy (`LQHuy0210`) | Deferred; no frontend route; backend/Gateway activation remains gated | [surgery.md](surgery.md) |
| Organization | Hoàng Anh (`TranHoangAnh94`) | Department, staff and account administration | [organization.md](organization.md) |
| Patient | Hoàng Anh (`TranHoangAnh94`) | Patient list/detail/create/update/delete | [patient.md](patient.md) |
| Billing | Lộc (`locgit-89`) | Invoice lookup/detail/create/payment | [billing.md](billing.md) |
| Notification | Lộc (`locgit-89`) | History/detail/manual send | [notification.md](notification.md) |

For every service, shared app shell, navigation, auth/session, `src/components/**`, `src/lib/**`,
packages, and config stay outside the service scope unless the task explicitly assigns them.
Subagents inherit the same boundary.
