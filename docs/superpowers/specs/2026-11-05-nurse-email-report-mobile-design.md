# NURSE patient email, Report demo data and mobile consistency

Date: 2026-11-05. Status: implementation following user refinement of Report UI.

## Authorized scope

The user requests correction of NURSE patient email permissions, more Report demo
data, removal of redundant Report UI, mobile form spacing and role-specific mobile
alignment with the web, particularly Billing. This request names Patient and Report
and supplies a task-scoped frontend override for those changes. Staff email changes
remain ADMIN-only. Deferred inpatient/surgery work remains outside scope.

## Findings from the current source

- PatientForm.tsx makes the email input read-only on every existing patient for
  non-ADMIN users, including NURSE and patients without accounts.
- PatientWriteApplicationService.update accepts patient profile email updates.
  AccountManagementController.changeEmail is ADMIN-only. PatientAccountController
  allows ADMIN/NURSE issuance, password reset and mail status/retry, but has no
  patient-scoped email change operation.
- SecurityAccountOperationsService.changeEmail already records an audit, updates
  credential version, invalidates recovery tokens, queues patient profile sync and
  notifies the previous/new email addresses. Credential mail operations require
  profile/login email synchronization.
- Mobile patient forms put outlined fields directly next to each other without
  consistent vertical separation. This is a source-level cause candidate; actual
  overlap with focus, validation, keyboard and large text still needs reproduction.
- Mobile Billing tabs are Hóa đơn/Khoản thu/Hoàn tiền/Báo cáo. Web cashier navigation
  is Xét nghiệm và thuốc/Phí khám/Lịch sử thu/Hoàn tiền, with Phí khám behind the
  existing scheduling gate. MANAGER access must be compared per action/API.
- Outpatient Report renders provisional statistics plus a separate reconciled
  report section. The user identified the long reconciled section as the redundant
  presentation and requested a more compact interface with richer demo data.
- Prior Report runtime evidence records sparse current facts and a historical
  synthetic accepted period. Current fixture counts have not yet been measured.

## Approach

Recommended: patient-scoped authorization, consistent mobile form spacing, targeted
Billing parity and a reproducible demo dataset. Extending the generic ADMIN account
endpoint to NURSE creates an unnecessarily broad authorization surface. A full UI
rewrite would expand the task without addressing the specific reported failures.

### Patient email and account issuance

NURSE and ADMIN can add or correct an active patient's email before account
issuance. When an account exists, use a patient-scoped operation that resolves the
account from the authoritative patient ID and verifies PATIENT role and ownership.
Never accept a staff account ID through this operation. Keep staff email operations
ADMIN-only in both Gateway and Core.

Reuse the audited email change workflow for existing patient accounts. Preserve
unique email validation, credential revocation, recovery invalidation, profile
synchronization and notifications. Block credential mail/issuance actions while
required email synchronization is incomplete and expose a recoverable UI state.
Keep transactions within each existing context boundary. No cross-context SQL.
The implementation must examine account/profile update races before selecting the
final command ordering.

Web and mobile expose the same capability in the patient profile/account section.
Before account creation, staff save the valid patient email and then issue the
account using the existing idempotency behavior. After account creation, show the
audited correction flow and refresh profile/account state after completion.

### Report data and redundant UI

Extend the maintained demo preparation flow with deterministic synthetic outpatient
visits and classified EXAM/LAB_TEST/PRESCRIPTION receipts, multiple departments,
multiple days and months, refunds and explicit empty periods. Size and date window
are configurable; the default should make the current report period informative.
Use authoritative existing department IDs and matching producer fixtures/contracts.
Keep repeat runs idempotent and retain a manifest with expected totals and counts.
Historical real receipts with unknown purpose remain unclassified. Do not fabricate
accepted coverage or auto-release held events to populate a chart.

Keep provisional statistics and reconciled publication semantics distinct. Collapse
the long lower reconciled section with a clear summary and an accessible disclosure.
Preserve explicit deep links and allow operators to open full evidence when needed.

### Mobile forms and Billing parity

Apply consistent vertical spacing to affected forms using existing Flutter
patterns. Let validation/help text grow, allow long dropdown labels to wrap or
truncate appropriately, and keep focused fields reachable with the keyboard open.
Preserve existing architecture and dependencies.

Compare each Billing task and role with web and backend permissions. Align cashier
navigation and starting screen around lab/drug collections, gated examination fees,
collection history and refunds. Preserve ADMIN/MANAGER reporting only where the
live API permits it; copying labels must not introduce broader access.

The bounded role-navigation comparison also found that web offers every human
role a signed-in-user profile, while mobile has no corresponding route or menu.
Close this concrete parity gap with a session-scoped self profile: staff may edit
only their own phone/address, patients view their own record read-only, and email
remains read-only in this screen. Existing managed-record account controls remain
separate. Already documented doctor scheduling and patient registration mobile
deferrals stay deferred.

## Verification required before completion

- Regression tests: NURSE adds email then provisions once; NURSE changes an existing
  patient's account email; old credentials/recovery are invalidated as required;
  NURSE cannot change staff email or bypass patient ownership; ADMIN retains access.
- Email conflict, archived patient, synchronization retry and uncertain/replayed
  command cases preserve existing security and idempotency guarantees.
- Web feature tests, typecheck/lint and production build for changed areas.
- Flutter analysis and focused widget/use-case tests, including narrow viewports,
  validation errors and increased text scale; actual UI checks with keyboard/focus.
- Billing role/action comparison against web and real Gateway contracts.
- Demo manifest totals match Report projections; repeat preparation does not
  duplicate data; provisional data is never labelled accepted by the UI.

## User refinement

The long "Báo cáo đã đối soát" section is the specific UI to optimize. Implement
progressive disclosure rather than deleting its accepted-report capability. Data
must be richer than the current single sparse historical example.
