# Mobile completion and Review3 screenshots

Approved by the user on 2026-10-22 after the source audit. Task scope covers all mobile feature folders, app/core integration and Review3 evidence. Existing web and backend contracts are read-only reference; no backend gate is weakened for screenshots. Inpatient and Surgery remain deferred.

Complete appointment detail/create/update/check-in/start/cancel, medical record lookup/create/update/diagnosis/completion/lab orders, Lab detail/create/start/results/cancel, and reports matching the current frontend and live controller authority. Reuse existing Organization/Patient/Billing/Notification/Pharmacy implementations, resolving remaining integration and usability gaps. All commands use authenticated Gateway contracts, server IDs and explicit confirmation for irreversible business actions. No automatic mutation replay.

Human references display patient/staff/department names from authorized APIs; selectors retain canonical IDs internally. Do not substitute guessed names or expose UUID as the default label. Missing permission, absent references and service errors have distinct states. Reference caches and private workflow state reset across sessions. Preserve financial readiness, idempotency and role checks.

Verify serialization, authorization UX, command bodies, session boundaries and names with tests; run analyzer, all mobile tests and web build. Run real Gateway workflows against preserved demo data. Screenshots are evidence only for actions actually observed. Keep unresolved backend limitations explicit.

Replace the entire Review3 screenshot section in its existing web/mobile and eight-role grouping. Add relevant interaction captures for completed mobile workflows. Capture lossless PNG at native render resolution, web at least 1920 by 1080 and phone 430 by 932 with high density where supported. Preserve the rest of the current report, including user edits and testing tables. Save a private backup, render the resulting Word and inspect every page before delivery.
