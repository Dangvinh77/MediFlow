# Profile account and clinical workflow changes — 04/11/2026

Baseline: `d9a1635b`. Branch: `codex/profile-workflows-web-mobile`.

## Scope

- Staff profiles expose account issue/reset, delivery metadata and audited email correction for ADMIN.
- Patient profiles expose a single issue action when no account exists and scoped reset/retry for ADMIN/NURSE when an eligible account exists. Passwords and email bodies are absent from these responses and screens.
- Profile lookup uses the owner UUID. The UI distinguishes unavailable lookup, no account, inactive account, archived patient and pending email synchronization.
- Patient creation from reception opens the new profile directly. The profile composes the account panel and a registration action without importing another feature.
- Native reception uses existing V2 sessions and registration contracts: named patient, department, doctor and room, capacity/revision, follow-up source and replacement consent, stable command keys, awaiting-payment receipt, refreshed capacity and next patient.
- Medical-record screens compose Pharmacy prescribing and record-linked prescription reads at the app boundary. The source context stays locked; closed records remain readable and do not permit a new prescription.
- Uncertain account, reception and prescribing commands retain their intent in the current authenticated app session. They are reconciled only by an explicit user action. No automatic mutation replay or persistent clinical-payload cache was added.

## Verification

Commands and final suite results are recorded in the accompanying implementation plan after integration checks.
Focused reception tests cover retry identity, response ownership, follow-up validation, success locking, capacity refresh failure, session replacement and 320 px layout with enlarged text.

This evidence does not constitute device/Gateway acceptance of the new flows. No screenshots or native-device acceptance are claimed for this change. Reception requires `MEDIFLOW_OUTPATIENT_UI_ENABLED=true` and a backend with scheduling enabled; prescribing also requires `PHARMACY_MODE=outpatient`.

## Remaining acceptance

Run the staff/patient email delivery, issue/reset, nurse registration and doctor prescribe/complete/reopen scenarios against a rebuilt backend and frontend on a real device. Verify SMTP delivery, delayed Patient email synchronization, and expiry/payment transitions. Existing broader mobile V2 workboard items remain separate; this slice does not complete every outpatient module.
