# Frontend Service Guides

Read the guide for the service you are changing after the global
[`frontend/AGENTS.md`](../../AGENTS.md),
[`12-frontend.md`](../../../docs/ai/12-frontend.md), and
[`15-frontend-ownership.md`](../../../docs/ai/15-frontend-ownership.md).

These guides contain the effective writable paths, current UI baseline, owner queue, and known
handoffs. Backend controllers and DTOs remain the wire-contract authority.

| Service | Owner | Current frontend baseline | Guide |
|---|---|---|---|
| Clinical | Vinh (`Dangvinh77` / `Harori`) | Appointment list and medical-record lookup | [clinical.md](clinical.md) |
| Lab | Vinh (`Dangvinh77` / `Harori`) | Lab queue/list | [lab.md](lab.md) |
| Pharmacy | Huy (`LQHuy0210`) | Drug and prescription workflows | [pharmacy.md](pharmacy.md) |
| Report | Huy (`LQHuy0210`) | Daily report | [report.md](report.md) |
| Organization | Hoàng Anh (`TranHoangAnh94`) | Department and staff reads | [organization.md](organization.md) |
| Patient | Hoàng Anh (`TranHoangAnh94`) | Spec-backed list; backend blocked | [patient.md](patient.md) |
| Billing | Lộc (`locgit-89`) | Patient invoice lookup | [billing.md](billing.md) |
| Notification | Lộc (`locgit-89`) | Patient notification lookup | [notification.md](notification.md) |

For every service, shared app shell, navigation, auth/session, `src/components/**`, `src/lib/**`,
packages, and config stay outside the service scope unless the task explicitly assigns them.
Subagents inherit the same boundary.
