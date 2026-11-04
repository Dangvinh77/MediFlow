# Email login, account delivery, recovery and demo mailbox

Date: 2026-11-04. Status: proposed design, pending user review; implementation has not started.

## User request and scope

Replace CCCD login with email. Send a styled email with system branding, temporary password
and login link when ADMIN issues staff credentials, ADMIN resets a human password, or an
authorized operator issues a patient account. Add public forgot/reset-password pages.
Lock email against non-ADMIN writes, permit CCCD correction independently of credentials,
and provide a simple role/account selector and HTML mailbox for existing synthetic seed users.

Task scope covers Core Organization/Patient, Gateway, account-security mail infrastructure,
the Organization/Patient frontend forms and matching routes, shared auth/login/profile UI,
demo mailbox UI, configuration examples, tests, and relevant contract/runbook documentation.
No change to unrelated clinical, billing, pharmacy or report workflows.

User clarified that frontend is not deployed. Provide a controlled intermediate access route,
initially on the local frontend, and make its origin configurable for later Vercel deployment.

## Findings verified from live source

- `CreateAccountService` accepts caller-provided username/password, has idempotency receipts,
  owner replacement policy and mandatory-change handling for initial password `1`.
- `ProvisionPatientAccountService` derives username from Patient CCCD and passes password `1`.
- `Account` currently disallows `@` and limits username to 50 characters.
- `VerifyCredentialsService` authenticates the persisted username and returns stable account,
  staff/patient IDs, credential version and mandatory-change state.
- `ManageAccountsService` supports ADMIN reset and self-change; password changes increment
  credential version. Gateway already enforces current account and mandatory-change state.
- `UpdateOwnStaffContactService` currently permits the signed owner to change email.
- Patient already permits authorized CCCD correction with reason and uniqueness protection.
- `MockEmailSender` currently only logs success. SMTP delivery and self-service recovery are
  not established by the existing email sender or current login UI.
- The current brand is the MF mark and MediFlow wordmark, blue `#1d4ed8`, pale slate background
  and white cards. No separate production logo image was found in `frontend/public`.
- Existing demo seed data uses synthetic `demo.mediflow.local` addresses. Existing stores
  still need an inventory for missing/duplicate emails before account migration.

## Options and selected proposal

1. Recommended: keep credential issuance, recovery and durable security-mail jobs under
   Organization authority, using one HTML/MIME renderer and two delivery adapters: Gmail SMTP
   for real accounts and a local mailbox for registered demo seed accounts.
2. Send security credentials through Support Notification/RabbitMQ. This reuses notification
   infrastructure but expands sensitive-payload retention and cross-context contract work.
3. Use a standalone catch-all SMTP inbox for every address. Simple locally, but real Gmail
   demonstrations would require changing delivery mode and it does not provide the requested
   role/name/email account selector directly.

## Account and profile rules

- Email becomes the sole human login identifier. Preserve account UUID, linked profile IDs,
  role, historical references, account state and existing password hashes during migration.
- Validate and normalize email consistently; enforce a database unique key on normalized login
  email. Expand username/storage/DTO limits to support email. Keep existing wire names where
  compatible, with UI labels saying Email; version incompatible issuance/reset contracts and
  update producer/consumer fixtures together. SYSTEM service credentials remain separate.
- Inventory existing accounts through authoritative profile mappings. Missing or conflicting
  email blocks that migration entry for ADMIN correction; never invent real-user addresses,
  silently merge accounts, replace an existing password or reset all accounts on restart.
- Account email is authoritative for login and recovery. Linked profile email must reflect
  ADMIN changes through public use cases; preserve separate stores and use explicit retryable
  synchronization where Organization/Patient cannot commit atomically.
- ADMIN-only email correction requires a reason, duplicate check, credential-version bump,
  recovery-token invalidation and notifications to old/new real addresses. Reject non-ADMIN
  email changes server-side, including attempted writes through general profile/contact APIs.
  Omitted email in an ordinary profile update preserves the current value.
- CCCD is independent of login. Keep audited correction and uniqueness validation. Preserve
  existing profile-edit permissions: staff identity correction by ADMIN; patient correction
  by ADMIN/NURSE. This request does not automatically grant PATIENT self-edit authority.

## Issuance and ADMIN reset

- Require a valid authoritative email before issuing an account. Resolve owner and role from
  the saved profile; retain the current ADMIN/NURSE patient-issuance permission boundary.
- Generate a cryptographically random temporary password server-side for issuance and ADMIN
  reset. Require password change on login, and invalidate existing credentials on reset.
- Atomically commit the credential mutation and a durable mail job. Replaying an idempotency
  key must not generate another password or another logical mail job. Recovery from lost HTTP
  responses uses the existing receipt pattern.
- SMTP runs outside the business transaction with bounded retries and explicit pending,
  accepted-by-SMTP and failed states. SMTP acceptance is not proof of inbox delivery.
- Protect temporary secrets required for retry with application encryption and an external
  key; delete secret payloads after delivery/expiry. Store only password hashes for login.
  No raw credentials in normal events, application logs, browser storage or Git.
- ADMIN UI reports delivery state and supports retrying delivery without silently resetting
  the password again. An explicit new reset invalidates the previous temporary password.

## Forgot password

- Public form accepts email and returns the same acknowledgement for known, unknown,
  disabled and ineligible accounts. Apply bounded per-origin and per-account throttles.
- Use a cryptographically random token, store its hash, bind it to account and current
  credential version, and expire it after 30 minutes. A new request invalidates prior tokens.
- GET opens the form without consuming the token. POST validates expiry and atomically
  consumes the token with the password update. Concurrent reuse permits one success only.
- Set a user-chosen password subject to the existing byte-length policy, clear mandatory
  change state, bump credential version, revoke old sessions and invalidate other reset links.
  Show a success page linking to login and send a password-change confirmation.
- Do not expose tokens to analytics/logging or third-party assets. Use no-store and a strict
  referrer policy on recovery pages. No automatic login after recovery.

## Email and access URL

- SMTP sender: `comflyuisdxlver1@gmail.com`; display name: `MediFlow`.
  Gmail host `smtp.gmail.com`, port 587, authenticated STARTTLS with required encryption.
  The supplied app password belongs only in ignored local/runtime secret configuration.
- Provide text/plain plus text/html MIME alternatives, Vietnamese copy, escaped user text,
  a blue brand header, embedded raster MF logo, greeting, clear CTA, fallback URL, expiry
  guidance where relevant and a footer. Use email-compatible tables and inline styles.
- Render issuance, ADMIN reset, recovery link, password-change and email-change templates.
  Reuse the same renderer for SMTP and demo mailbox.
- Configure `MEDIFLOW_PUBLIC_WEB_URL`, initially `http://localhost:3000`. Email links use
  `/account-access` on that origin: fixed-purpose login links go to `/login`; reset links
  go to `/reset-password` retaining the opaque token. Never accept an arbitrary redirect URL.
- Later set the origin to the Vercel deployment and redeploy configuration. Already-sent links
  retain their original origin; a local URL only opens on the machine hosting the frontend.

## Demo mailbox

- A demo-only feature, disabled by default outside the explicit local/demo configuration.
  The public demo entry can be reached from login so recovery is demonstrable while logged out.
- Select role, search by name or email with server pagination, choose a registered synthetic
  seed account, then view its inbox and message detail with subject, sender, recipient and date.
- Eligibility comes from existing authoritative seed receipts/account mappings, never only
  email suffix, a browser flag, supplied role or a guessed UUID. SYSTEM is excluded.
- Eligible synthetic recipients go to the local mailbox and are not sent through Gmail.
  Real recipients go through SMTP and never appear in the selectable demo inbox.
- Store delivered rendered messages for demo accounts so they survive restart; retain the
  same body/subject/assets as the generated MIME message. Serve embedded logo resources locally.
- Render in a sandboxed HTML frame with scripts, forms and external content blocked. Allow
  only controlled system login/reset links to navigate the app. Include loading, empty,
  error/retry and selection-change handling. Do not claim to implement Gmail's entire renderer.
- Demo endpoints and account search must be inaccessible when the backend demo flag is off,
  even if someone opens the route directly. Limit retention of demo credentials/reset links.

## UI deliverables

- Login uses Email and includes Forgot password plus demo-only Mailbox entry.
- Forgot password, reset password and access redirect routes use the existing theme and fields.
- Staff/patient create-and-issue flows require email and show delivery status instead of
  displaying password `1`; ADMIN reset generates and emails temporary credentials.
- Personal profiles display locked email; ADMIN forms provide audited email correction.
- Authorized CCCD correction remains available and never changes login email.
- Mailbox provides role filter, account search, inbox list and full HTML message pane.

## Verification and acceptance

- Lock current controllers, request/response DTOs, serializers, migrations, producer/consumer
  tests and Gateway rules before contract edits, per M6-02. Append migrations only.
- Unit/web tests cover email normalization/duplicates, role checks, non-ADMIN write attempts,
  CCCD independence, random passwords, mandatory-change state, idempotency and mail failures.
- PostgreSQL tests cover upgrade data conflicts, unique-email races, durable-job rollback,
  replay, token single-use races, expiry and credential/session invalidation.
- Gateway tests cover public recovery allowlists, service-only internal calls, rate limiting,
  normal role behavior, disabled accounts and restricted first-login sessions.
- Frontend tests and browser checks cover create-and-issue for staff/patients, email editing
  permissions, forgotten password success/expired/reused links and mailbox selection races.
- Run focused Core/Gateway checks and relevant frontend tests, lint, typecheck and build.
  Run required Docker integration checks and record actual failures/skips explicitly.
- Demonstrate real SMTP with a real recipient when available; verify Gmail logo, HTML and links.
  Separately demonstrate seed reset/recovery through the mailbox and persistence on restart.
  Never use synthetic seed addresses as real SMTP delivery targets.

## Review point

Approve the proposed behavior, especially random temporary passwords, email-only login and
seed-only demo mailbox. The supplied SMTP identity is enough to prepare configuration;
actual authentication/delivery and a reachable deployed origin still require runtime verification.
