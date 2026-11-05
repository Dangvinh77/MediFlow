# Patient email, Report and mobile verification — 2026-11-05

Implementation branch: `codex/nurse-email-report-mobile`, based on `c586e274`.

## Patient email

The scoped patient account command is `PUT /api/v1/org/patient-accounts/{patientId}/email`
with `{email, reason}`. NURSE and ADMIN may use it. Staff account email management
continues through the ADMIN-only account management route. Updating an unlinked
patient profile can add or correct its email before account issuance; a linked
profile uses the audited account command so the login and profile synchronize.
A mismatched patient profile can request synchronization again with the verified
current username and a reason. Credential issuance/mail stays blocked until the
emails match; the web includes an explicit synchronization recheck button.

Web verification: 33 Patient/Organization test files, 252 tests passed. TypeScript
and the affected Patient/Organization lint checks passed. Mobile profile account
verification: 12 tests passed, including patient owner validation, normalization,
NURSE email correction, issuance, archived profiles, pending synchronization and
uncertain password reset recovery and repair of a mismatched profile.

A browser check used mocked Gateway responses and a synthetic NURSE session on
the isolated web application, at 390 × 844 pixels. It verified an editable email,
successful profile save, patient account issuance, and a subsequent scoped email
change with a reason. The mock confirmed the login and profile email matched
afterward. There was no horizontal page overflow. These screenshots
verify browser behavior; they do not represent a live backend acceptance test.

- [Patient email saved, ready to issue](nurse-patient-email.png)
- [Issued patient account and email controls](nurse-linked-patient-account.png)

## Report browser behavior

Using mocked Gateway responses, the Report page was checked at 1440 × 1000 and
390 × 844 pixels. Operational charts remained visible, the reconciled section
started collapsed and opened from its summary, and neither viewport had
horizontal page overflow. The fixtures are synthetic browser test data.

- [Compact desktop Report](report-desktop-compact.png)
- [Expanded Report at a narrow width](report-narrow-expanded.png)

Focused Report verification passed 153 web tests across 14 files and 20 Flutter
tests. Report lint and focused Flutter analysis passed. The full frontend suite
passed **1,384 tests across 156 files**, including the page-level patient email
flow for both ADMIN and NURSE. Full frontend production build and TypeScript passed;
full lint has no errors and retains two existing Pharmacy hook warnings outside
the changed code.

## Backend regression verification

The final Core/Gateway Maven reactor exited successfully. Surefire XML reports
contain **2,386 passing tests**, with no failures, errors or skipped tests:
477 Core tests across 76 suites and 1,909 Gateway tests across 23 suites.
The PostgreSQL/RabbitMQ integration tests include correction racing with account
issuance: the account and queued mail recipient use the committed corrected email.
Patient profile changes and issuance share an Organization owner lock while Patient
keeps its independent transaction manager. Staff email remains ADMIN-only.

## Final integrated verification

Fresh runs on the final tree, based on `c586e274`:

| Gate | Result |
| --- | --- |
| Core + Gateway Maven | 2,386 passed (477 + 1,909), 0 failed, 0 skipped |
| Frontend Vitest | 1,385 passed across 156 files |
| Frontend TypeScript, production build, affected lint | Passed |
| Flutter tests | 489 passed |
| Flutter analyze | No errors or warnings; one existing `info` in an unchanged Pharmacy file |
| Python demo tools | 69 passed |

One earlier full Vitest run failed a focus assertion in the unchanged
`DoctorWorkShiftsWorkspace.ux.test.tsx`; it passed 5 of 5 isolated runs and the next full run. It is load-sensitive
and unrelated to this change.

Late fixes made during this closeout:

- The exam-fee queue overflowed by 31 px at keyboard height because `viewInsets` is always
  zero inside a `Scaffold` body. It now uses one stable scroll layout, which also keeps the
  focused field.
- "Xác nhận nghĩa vụ 0 đồng" sat inside the `remaining > 0` block, so it could never appear
  on a zero-balance request. It now renders outside that block.
- A local change pointed the iOS Profile configuration at `Info.plist`, reversing the demo
  HTTP fix, to satisfy an outdated `test_mobile_lan_config` assertion. The configuration was
  restored and the test now asserts Debug/Profile/Release explicitly.

## Live Gateway verification

A fresh disposable stack (`rehearse_snapshot.py --scheduling --prepare-report-demo --keep
--port-offset 10000`) restored, seeded and accepted the Report fixture from empty databases
with 2 accepted synthetic periods. [13 of 13 NURSE steps passed](nurse-gateway-live.json):
add email, issue account, profile PUT and blank email refused on a linked patient (422),
reason required (400), login email corrected, profile synchronized, CASHIER denied (403),
staff route denied (403), staff ID on the patient route refused (404) and duplicate login
email refused (422).

The stack needs `MEDIFLOW_SECURITY_MAIL_KEY`. Without it, issuance and email correction fail
with 503 `ORG_LOOKUP_UNAVAILABLE` because credential mail cannot be encrypted. A generated
test key was used, with `MANAGEMENT_HEALTH_MAIL_ENABLED=false`. Mail was queued only; delivery
to a real mailbox was not verified.

## Mobile exam-fee flow

The Flutter web build ran against the same kind of live stack at phone width. A NURSE
registration appeared in the CASHIER "Phí khám" queue, and the cashier collected 50,000 VND
in cash; the request ended "Đã thanh toán" with a transaction ID.

The tab is compile-time gated, so a build without the dart-defines cannot show it. Every demo
build path now passes them, and a Python test keeps it that way: `tool/run_web.ps1 -Demo`,
the iOS workflow, the launcher command, `Run-ClientChecks.ps1` and the README command.

This run also found and fixed raw UUIDs and UTC times in the queue and collection screens, a
UUID-only patient filter, and a false "không khớp" warning after a completed payment.

The exam-fee queue only lists requests whose 15-minute hold has not expired.

Other mobile behaviour was verified by widget tests, including 320 px, 1.45 text scale and
keyboard height. Nothing was run on a physical device.
