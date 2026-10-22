# Mobile Pharmacy — code integration, 2026-10-22

**11/12 task code, MP-HUY-01–11. Task12 actual acceptance NOT RUN.**
User authorized shared mobile network/app/config, then explicitly chose to finish code and test
the real environment later. This checkpoint supersedes earlier shared dependency status.

Production code includes optional POST Idempotency-Key, bodyless PUT, real Pharmacy create/
dispense adapters, reactive root DI/session/mode, nested routes and role guards. No fixture
runtime, backend gates, package/platform changes or new branch/commit/push were introduced.
PHARMACY_MODE defaults to unavailable; outpatient/legacy must match the confirmed backend.

Local verification is recorded in [verification.json](verification.json), including final source
SHA-256 hashes and check results. Tests use an HTTP double and exercise the production composition
root; they do not prove live issuer authorization, clearance, inventory effects or native readiness.
The check script builds with the default unavailable mode and Gateway localhost:4300/api.

The previous runtime checkpoint documents service startup only. No new login or clinical workflow
was executed for this candidate after the user deferred real acceptance. Android/iOS/Windows
native acceptance remains NOT RUN. The existing CupertinoIcons font warning remains visible in
the web build; the build result and warning are recorded separately.

Remaining actual acceptance: real workflow record/episode/staff/patient/department and confirmed
priceCode; DOCTOR create → CASHIER public payment → PHARMACIST refresh/dispense, unpaid/terminal/
retry/clearance unavailable, cross-doctor denial and one inventory effect; real touch/keyboard/
session/role matrix and native devices. No IDs, account credentials or tokens are stored here.
All remaining coordination stays in the single shared Huy handoff.
