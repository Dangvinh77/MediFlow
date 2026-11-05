# Nurse email, Report and mobile implementation plan

> Execute with the executing-plans/subagent-driven-development skills. Independent
> paths may use dispatching-parallel-agents; reviewers inspect the integrated diff.

**Goal:** Enable NURSE patient email corrections and issuance, compact Report
reconciliation, expand realistic repeatable demo data, and fix mobile form/Billing UX.

**Architecture:** Keep patient-scoped account commands separate from ADMIN staff
management. Reuse audited email change and context sync. Preserve Report coverage
semantics; make details progressive disclosure. Extend existing demo preparation.

**Stack:** Spring Boot/Maven, Next.js/TypeScript/pnpm, Flutter/Dart, Python demo tools.

## 1. Patient email and account lifecycle

- [x] Lock current controller/service/Gateway/DTO fixtures and profile email update paths.
- [x] Add failing Core/Gateway regression tests allowing ADMIN/NURSE patient email
  correction while rejecting staff accounts and other roles. Keep mail synchronization.
- [x] Implement `PUT /api/v1/org/patient-accounts/{patientId}/email` with validated
  email/reason, authoritative patient-account resolution and existing audited change.
- [x] Update web Patient form and shared owner account panel to expose the scoped
  operation for NURSE; keep staff controls ADMIN-only. Add meaningful UI regressions.
- [x] Update mobile account repository/use-case/panel with the same command and tests.
- [x] Run focused tests then full affected Core/Gateway suites; typecheck/lint web.

## 2. Compact reconciled Report UI

- [x] Inspect web OutpatientReportWorkspace/Result/View and matching mobile Report.
- [x] Add behavior tests for a collapsed reconciled section and usable disclosure.
- [x] Keep statistics prominent; collapse reconciled controls/evidence with clear
  labels and preserve direct-link/filter/history behavior and error/coverage semantics.
- [x] Run Report web tests/typecheck/lint and mobile Report widget tests.

## 3. Realistic deterministic demo Report seed

- [x] Inspect maintained snapshot generator/preparation and existing fixture manifests.
- [x] Test multiple days/months/departments, linked categorized receipts/refunds,
  totals, valid IDs and repeatability before changing generator behavior.
- [x] Extend the maintained demo seed with an informative current period and multiple
  accepted synthetic periods using the existing acceptance pathway. Preserve any
  real/unclassified history and do not synthesize real coverage acceptance.
- [x] Run Python demo tests and available disposable-runtime consistency checks;
  document the exact command to populate the new dataset and measured counts.

## 4. Mobile forms and Billing

- [x] Reproduce affected forms and compare Billing actions/roles to current web.
- [x] Fix spacing in actual affected form layouts; support validation growth, narrow
  screens, long labels and keyboard scrolling without changing field contracts.
- [x] Align cashier Billing navigation/tasks with web (including gated exam fees),
  preserving ADMIN/MANAGER reporting permissions and existing command safeguards.
- [x] Close the concrete role navigation gap found during comparison: signed-in
  users can open their own profile; staff edit only phone/address and patients
  remain read-only. Use signed identity claims and preserve session/owner guards.
- [x] Run focused widget regressions, Flutter analysis/full tests and web build.

## 5. Review and evidence

- [x] Update canonical contracts/workboards for changed permission and demo behavior.
- [x] Review scope and integrated diff, resolve spec/security/quality findings.
- [x] Record fresh test counts/limits and open a pull request for review.
