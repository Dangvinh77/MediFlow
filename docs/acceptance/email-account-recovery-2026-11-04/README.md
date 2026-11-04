# Email account recovery — local verification, 04/11/2026

Branch: `codex/email-account-recovery`; baseline: `4e1942bb`. Working-tree changes are uncommitted. This record covers only observed checks; it does not certify a remote deployment or actual Gmail inbox delivery.

## Completed frontend checks

- Final `pnpm exec vitest run --maxWorkers=2 --testTimeout=15000`: exit 0, **149 files / 1,315 tests passed**. Default 5-second runs under concurrent backend load hit timing/focus failures in existing Report/Inpatient tests; the Report file passed on focused rerun and the final full run passed without changing those features.
- Focused Organization/Patient/login/API run: exit 0, **34 files / 275 tests passed**.
- After the last response-order adjustment, focused account-security/recovery/mailbox components: exit 0, **3 files / 10 tests passed**.
- `pnpm typecheck`, `pnpm lint`, `pnpm build`: exit 0. Build includes `/account-access`, `/forgot-password`, `/reset-password`, `/demo-mailbox`.
- Independent source review identified malformed HTTP-200 write responses as uncertain outcomes; fixed with shared outcome classification and network/malformed-response regression coverage. Patient issuance now uses its separate API and preserves its idempotency key after lost responses/remounts; focused AccountAdmin regression: 10 tests passed. Final reviewer recheck found both the issuance and duplicate-email backfill blockers resolved.
- Browser on local production frontend at port 3107: login uses Email and has forgot-password link; recovery form uses system theme; intermediate login URL reached `/login`; synthetic preview token URL reached `/reset-password?token=demo-preview`. No credential-changing UI was submitted.
- Final production browser check: missing reset token displays an invalid-link message and disables save.
- Recovery HTTP response observed `Cache-Control: no-store`, `Referrer-Policy: no-referrer`, `X-Robots-Tag: noindex, nofollow`.

## Gateway checks

- `mvn -q -pl backend/gateway -am test`: exit 0. Current Gateway Surefire XML totals **1,830 tests, 0 failures, 0 errors, 0 skipped**.
- Public exact-path/method rules, mailbox-disabled gate, socket-IP throttling and proxy behavior are covered by new focused tests.
- Request-capture test preserves Vietnamese text and email `@` after exactly one URI decode.

## Configuration checks

- `docker compose config --quiet`: exit 0 with new environment wiring.
- `smtp.local.env` is ignored by `.gitignore` (`*.env`) and untracked. `smtp.env.example` contains placeholders; `.dockerignore` excludes the local secret file from build context.
- Safe local credential scan found neither the SMTP password nor the local encryption key in changed shareable files.
- Gmail STARTTLS/authentication/NOOP succeeded using the ignored local config. **No real email was sent by this check.**

## Core checks

- Final `mvn -q -pl backend/core-service -am test`: **exit 0; 438 tests, 0 failures, 0 errors, 0 skipped**. An earlier handle expired before exit capture and logged Rabbit/Surefire shutdown delays; the final rerun persisted and confirmed exit 0. PostgreSQL/Testcontainers executed Organization migration v16 and Patient migration v5.
- Coverage includes generated credentials/idempotency, single-use recovery and expiry, email change authorization/audit, seed mailbox isolation, encrypted persisted mail, SMTP MIME and concurrent patient issuance.

## Packaged runtime checks

- Packaged Core and Gateway build: exit 0. Isolated PostgreSQL Organization/Patient stores, RabbitMQ and Eureka; Core port 18089, Gateway port 18080, frontend port 3107. Existing user data/services were not used.
- SMTP dispatch was disabled and mail health disabled only for this isolated test runtime; no Gmail credentials were loaded or real email sent.
- Initial runtime recovery revealed PostgreSQL JDBC rejecting directly bound `Instant` values. Added actual-PostgreSQL regressions and converted rate-limit, recovery-token save/invalidate/mark-used and staff identity audit parameters to `Timestamp`; focused regression tests passed.
- Public Gateway forgot-password queued a seed recovery email. Full HTML includes embedded MediFlow logo and the configured `/account-access` reset link. Reset succeeded; replay returned HTTP 422; login with the new password returned PATIENT. Confirmation mail was persisted as well. No password/token was printed in the API smoke report.
- Browser verified role filtering and Vietnamese-name/email search, opened the actual seed email and visually inspected the logo, full HTML layout, reset button and footer in the sandboxed iframe.

- New staff profile/account issuance through Gateway returned a PENDING mail receipt with SMTP dispatch disabled. Its non-seed email was excluded from the demo mailbox.
- ADMIN reset of a seeded patient with an empty request body returned SENT; replay with the same Idempotency-Key returned the same message ID. The mailbox held a full HTML temporary-credential email, and login using that emailed temporary password succeeded.
- Temporary Gateway/Core/Eureka processes and the three named isolated database/RabbitMQ containers were stopped after verification. The frontend was rebuilt with its default Gateway origin `http://localhost:8080` (exit 0), restoring the normal local startup configuration.

## Verification limits

No remote deployment or actual Gmail inbox delivery was tested. SMTP authentication/NOOP and local MIME tests passed; the packaged runtime used the simulated seed mailbox and disabled external delivery. Existing application data was not modified. All changes remain uncommitted; the ignored local SMTP file is not tracked.

## Follow-up: automatic SMTP loading and Wi-Fi mobile demo

- `start-backend.bat` imports `smtp.local.env` inside its PowerShell launcher before starting the snapshot runner and Java applications. The local file stays ignored; the safe credential scan passed again after the launcher/mobile changes.
- The launcher selects an active physical adapter with an assigned private IPv4 address. Gateway binds all interfaces on port 28080 in LAN mode; internal applications remain on loopback. Frontend binds port 3000 and the default local email origin is changed in the running process to the selected LAN address. An explicitly configured remote web origin is preserved.
- Firewall setup is limited to TCP 28080/3000, the selected adapter and `LocalSubnet`. This session was not elevated, so actual Windows Firewall rules were not installed. Administrator/non-administrator behavior and rule scope passed the mocked PowerShell regression checks.
- Python snapshot/mobile configuration regressions: 27 tests passed. Launcher cleanup safety suite: 8 scenarios passed. LAN selection, invalid-address rejection, loopback and firewall-scope checks passed.
- Final Core Maven test/package verification after receipt-backed snapshot mailbox registration: exit 0, 442 tests, no failures/errors/skips. The earlier packaged six-deployment build also exited 0.
- `mobile/tool/check.ps1` exited 0: formatting, analysis, application/loader tests and web build. iOS Debug uses its own local-network/HTTP plist; Release/Profile retain the original plist. Native iOS/Android execution and a connection from an actual phone were not verified on this Windows host.
- The first isolated LAN smoke stopped at readiness because the SMTP health indicator was DOWN without SMTP credentials. The retry disables mail health only in the test process, alongside disabled external mail dispatch; normal launcher SMTP behavior is unchanged.
- The next attempt exposed an existing mismatch between Compose's Pharmacy database name (`mediflow_pharmacy_docker`) and the immutable snapshot (`mediflow_pharmacy`). The snapshot runner now maps that name to the restored isolated database; a regression covers this mapping and preserves other context names. No Compose production store or snapshot SQL was changed.
- LAN HTTP checks exposed that Patient seed-profile receipts live in Patient's store, so Organization's receipt join registered staff only. Patient mailbox registration now requires its existing account receipt plus the exact fixture email verified through the Patient identity port. The new regression covers registration without an Organization profile receipt, repeat startup and exclusion after profile email changes. No cross-context database access was added.
- Final packaged snapshot `snapshot-20261104-071527` reached READY with all eight applications healthy. Requests to `http://192.168.1.3:28080` returned health UP, successful seeded ADMIN email login, two Patient mailbox accounts, forgot-password HTTP 200 and an actual HTML recovery email containing the LAN `/account-access` URL. Only Gateway listened on all interfaces; all seven other applications listened on `127.0.0.1`. See [safe LAN smoke output](lan-runtime-smoke.json). This verifies access through the host's LAN address, not inbound access from another device.
- The final runner stopped through its own stop file and exited 0. Its disposable containers and Gateway listener were gone after cleanup; existing user services/data were preserved.
