# Mobile clinic flow — 03/11/2026

Candidate: `3e28d96e` plus the clinic usability changes committed with this evidence.
Execution: Flutter web, phone viewport 390 × 844, UI `localhost:4301`, same-origin
Gateway proxy to disposable rehearsal `127.0.0.1:28080`. Real packaged services,
PostgreSQL and RabbitMQ ran from current source using `rehearse_snapshot.py --keep`.
Only synthetic demo accounts/patients were used. No real money was transferred.

## Observed flow

| Step | Role | Observed result |
|---|---|---|
| Create appointment | NURSE | Nguyễn Văn An Demo, 03/11/2026 16:00, reason `MOBILE E2E 03-11 - dữ liệu thử nghiệm tổng hợp`; saved successfully |
| Check in | NURSE | Appointment moved to `AWAITING_PAYMENT` |
| Record exam cash payment | CASHIER | 150000 VND, `PAID`, remaining 0; see assistance limitation below |
| Start examination | DOCTOR | Appointment became `IN_EXAM`; mobile opened the exact visit record |
| Record symptoms and diagnosis | DOCTOR | Both persisted and were present after logout/login |
| Order CBC | DOCTOR | Clinical source order and Lab request both created through mobile; fixture price code `LAB` |
| Collect Lab fee | CASHIER | Patient name filter returned one request; 80000 VND, `PAID`, remaining 0 |
| Perform and publish Lab result | LAB_TECH | Clearance present; `READY` → `IN_PROGRESS` → `COMPLETED`; WBC (TEST), 6.5 G/L, reference 4.0–10.0; synthetic conclusion persisted |
| Read Lab result | DOCTOR | Same patient/result visible in Lab detail using doctor permissions |
| Complete examination record | DOCTOR | `Đã hoàn tất`, disposition `OUTPATIENT_FOLLOW_UP`, completedAt `2026-11-03T15:11:48.892881Z` |

Appointment identifier: `1cf2a254-b4cd-49ac-ad38-6c5553832290`.
The user stopped further testing after record completion. The final appointment
status was not re-read, so this evidence confirms record completion only.

## Limits and findings

- **Assisted exam-fee handoff:** mobile Billing queue supports Lab/prescriptions,
  not EXAM. Reception detail does not expose the codes required by the manual
  collection form. A read-only Gateway request under the nurse demo account
  retrieved the newly created appointment/patient identifiers; those identifiers
  were then entered into the mobile form. All payment writes used mobile UI.
  Therefore this is a completed assisted flow, not an unassisted UI-only acceptance.
- Appointment time picker permits an outside-hours selection. At 22:02 the
  backend rejected it with `Appointment time must be between 07:00 and 17:00`.
  Selecting 16:00 through the picker succeeded. Client-side hours guidance and
  localized validation remain follow-up work.
- The doctor read the result through the Lab menu, then returned through the
  appointment's `Tiếp tục khám`. Results are not embedded in the record workspace.
- No prescription/dispensing, cancellation/refund, scheduling V2, patient results,
  email recovery, native device layout or iOS acceptance is claimed by this run.
- Mail health/dispatch were disabled only in the disposable rehearsal process;
  no production configuration was changed. No credentials or tokens are retained.

## Screenshots

- [Lab fee paid, zero balance](lab-payment.png)
- [Published Lab result](lab-completed.png)

## Candidate checks completed before the interactive run

- Frontend: 150 test files, 1319 tests passed; typecheck, lint and production build passed.
- Flutter: 385 tests passed; format/analyze and web build passed; Android debug APK built.
- These automated results do not imply all mobile workboard items are complete.
