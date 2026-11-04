# Email Account Recovery Implementation Plan

> **For agentic workers:** Use subagent-driven-development for the backend task and root implementation for frontend integration; review the integrated change before completion.

**Goal:** Email-only human login, emailed issuance/reset credentials, single-use self-service recovery, immutable self-service email, editable CCCD and a seed-only HTML mailbox.

**Architecture:** Organization owns credentials and transactional security mail. Gateway exposes exact public recovery/demo routes and protected account operations. Web uses its existing gateway API wrapper and feature folders.

**Tech Stack:** Spring Boot, PostgreSQL/Flyway, JavaMail, Next.js, TypeScript, Tailwind, Vitest.

**Spec:** `docs/superpowers/specs/2026-11-04-email-account-recovery-design.md` (user approved).

## Global constraints

- Preserve existing UUIDs, data stores, role boundaries, mandatory password change and credential revocation.
- SMTP credentials stay in ignored `smtp.local.env`; never log or commit them.
- Public web origin starts at `http://localhost:3000`, configurable for Vercel.
- Never send synthetic seed mail to Gmail. Demo endpoints require backend demo enablement and verified seed eligibility.
- Append migrations, lock source fixtures and mirror exact produced DTOs before UI integration.

## Review focus

- Existing missing/duplicate profile email must not silently assign credentials to another person.
- Concurrent token consumption and idempotent issuance must not generate multiple credentials.
- Non-ADMIN attempts to change email through any profile endpoint must fail server-side.
- Demo selection cannot expose real accounts or allow scripts in rendered mail.
- Delivery retry must keep the issued secret and avoid a second password reset.

## Task 1: Backend account security and delivery

Files: Core Organization/Patient domain, application ports/services, infrastructure configuration/persistence/mail, controllers/DTOs, append migrations; Gateway auth/security/routes; corresponding tests.

- [x] Lock live source DTOs, migrations, security declarations and seed receipt authority.
- [x] Add failing tests for email identity, issuance/reset mail, self email lock, CCCD independence, expiry/single-use token, demo isolation and idempotency.
- [x] Implement email migration and validation, generated temporary credentials, durable encrypted mail jobs, SMTP/demo adapters and HTML/MIME templates.
- [x] Implement recovery, audited ADMIN email correction and exact Gateway allowlists.
- [x] Run focused tests then required Core/Gateway integration suites; report exact results.

## Task 2: Frontend and API integration

Files: shared auth API/login; new forgot-password/reset-password/account-access/demo-mailbox routes; Organization APIs/types/account forms/reset/contact/staff identity; Patient email permissions; matching tests.

- [x] Add public recovery forms and controlled redirect plus seed mailbox with sandboxed HTML and no third-party resource loads.
- [x] Replace initial password/CCCD issuance UI with authoritative email and delivery feedback.
- [x] Lock profile email to ADMIN and enable audited staff CCCD correction while retaining patient correction.
- [x] Add meaningful API/component regression tests, run typecheck/lint/build and focused tests.

## Task 3: Integration review, runtime checks and documentation

- [x] Review full implementation against approved spec and security failure cases, fix findings.
- [x] Verify ignored local config, add explicit startup/config documentation and source contract lock.
- [x] Run browser smoke and actual SMTP checks if runtime is available; distinguish unverified Gmail delivery from passing tests.
- [x] Record remaining limitations and changed files without including secrets or claiming unrun checks.

## Frontend evidence checkpoint

- 2026-11-04: full Vitest run, 149 files / 1,314 tests passed; focused Organization/Patient/login/API run, 34 files / 275 tests passed.
- ESLint, TypeScript and Next production build passed; browser verified login, recovery forms and the two intermediate redirect purposes.
- Independent frontend review finding (malformed HTTP 200 response treated as known failure) fixed with `isSaveOutcomeUnknown` and regression coverage.
- Final frontend run after the patient issuance fix: 149 files / 1,315 tests passed (`--maxWorkers=2 --testTimeout=15000`); default timeout runs had timing failures in existing Report/Inpatient tests under concurrent backend load. Those features were not changed. Final lint/typecheck/build passed.
- Final independent recheck cleared the patient issuance endpoint and duplicate-email backfill findings. Backend packaged-runtime acceptance is recorded separately.

## Final backend and runtime evidence

- Core Maven exit 0: 438 tests, no failures/errors/skips; Gateway exit 0: 1,830 tests. Packaging exited 0.
- Actual PostgreSQL regressions reproduced and fixed timestamp binding in rate limits, recovery tokens and staff identity audits.
- Packaged Gateway/Core smoke passed recovery, full HTML mailbox, single-use reset/login, staff issuance, non-seed mailbox isolation and idempotent ADMIN reset/temp-password login.
- Browser visually verified the actual email logo, HTML content, button and footer. Temporary runtime was stopped; frontend final build restored default Gateway8080.
- Gmail authentication was verified; real inbox delivery was not attempted. See `docs/acceptance/email-account-recovery-2026-11-04/README.md`.

## Task 4: User-requested SMTP launcher and Wi-Fi mobile demo

Design: keep the existing disposable snapshot topology, import the ignored SMTP file inside the launcher process, expose Gateway28080/web3000 on the selected physical LAN adapter, keep internal apps/DB/broker on loopback. Print mobile Gateway URL and allow local-only/explicit-address options. Firewall rules cover only local-subnet traffic on the selected adapter. iOS HTTP exception is Debug-only; Release/Profile stay on the original plist. Snapshot bootstrap uses email authentication and never publishes or chooses generated account credentials.

- [x] Integrate SMTP loading and LAN selection/firewall setup in the launcher used by `start-backend.bat`.
- [x] Prepare frontend link origin and native mobile debug configuration; align mobile login with email.
- [x] Update snapshot preparation for email login and verified seed-mailbox registration without enabling seed creation.
- [x] Run launcher safety/network tests, Python regressions, Core tests and mobile check.
- [x] Verify packaged snapshot Gateway via LAN IP and record native/firewall limits.

Final follow-up verification: Core test/package exit 0 (442 tests, no failures/errors/skips), Python 27 tests passed, PowerShell launcher safety 8 scenarios plus LAN/firewall checks passed, mobile full check exit 0. Packaged eight-application snapshot reached READY; host LAN HTTP verified health, ADMIN email login, two Patient mailbox accounts and forgot-password HTML email with the LAN web origin. Fixed snapshot Pharmacy store mapping and Patient mailbox registration through its identity port after runtime checks. Actual phone/native builds and elevated firewall installation remain unverified on this Windows session. See the follow-up section in `docs/acceptance/email-account-recovery-2026-11-04/README.md`.
