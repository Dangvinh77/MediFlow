# Review 1 Tasksheet Design

## Objective

Create one English task sheet for Review 1 that records delivered frontend work from the live
MediFlow source and Git history, then schedules missing or future frontend functions under the
correct developer ownership.

## Deliverable

The deliverable is:

`docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/Tasksheet_review1.md`

It must remain a standalone Markdown file that can be copied or converted to Word.

## Header Structure

Use an HTML table inside the Markdown file so the two-level header has real merged cells:

- Independent columns: `No.`, `Member`, `Contents`, and `Page Name`.
- `Activity Plan` is the parent of `Planned Start Date`.
- `Plan Preparation Date` spans the child columns `Actual Start Date`, `Actual Days`, and `Status`.

The resulting table has eight leaf columns.

## Evidence and Date Rules

- Source routes, feature components, frontend service guides, the frontend workboard, and the
  canonical ownership matrix define the task contents and owner.
- Delivered work uses the earliest relevant Git author date as `Actual Start Date`.
- `Actual Days` is the number of distinct Git author dates that touched the relevant owned paths,
  not elapsed calendar time.
- Delivered work uses the known activity-plan or implementation start date as `Planned Start Date`.
- Future work is planned from `2026-10-12` in dependency-aware order.
- `—` is used when an actual date or actual-day count does not yet exist.

## Ownership

| Member | Owned frontend scope, including planned work |
|---|---|
| Vinh (`Dangvinh77` / `Harori`) | Appointment, medical record, Laboratory, Inpatient |
| Huy (`LQHuy0210`) | Pharmacy, Report, planned Surgery |
| Hoàng Anh (`TranHoangAnh94`) | Organization, Patient, identity liaison and patient self-service contracts |
| Lộc (`locgit-89`) | Billing and Notification |

Shared test harness, authenticated browser smoke tests, login/session integration, navigation, and
other shared files must be labelled `SHARED` in the activity description while retaining one named
coordinator. Ownership is not broadened by the task sheet.

## Status Vocabulary

- `DONE`: source route and required workflow are present.
- `IN PROGRESS`: implementation is present but the bounded workflow remains unfinished.
- `PLANNED`: future frontend work whose required contract is available or expected.
- `VERIFY CONTRACT`: implementation must first be checked against the live backend contract.
- `BLOCKED`: a named producer contract or activation decision is missing.

## Content Organization

Rows are grouped by member. Within each member group, delivered work appears first, followed by
testing/release work and future functions. Each future row names the dependency that prevents the
document from presenting it as delivered.

Page names use real Next.js route paths where a route exists. Planned routes are marked `Planned`
and remain within the owner's canonical bounded context.

## Acceptance Checks

1. The file contains all four team members.
2. Every current frontend route group is represented by its canonical owner.
3. Missing future frontend functions are assigned without crossing ownership boundaries.
4. Dates for delivered work are reproducible from Git history.
5. Future dates start on or after `2026-10-12`.
6. The header has the approved parent-child structure.
7. No task claims a blocked feature is already delivered.
8. The document contains no internal documentation-version labels.
