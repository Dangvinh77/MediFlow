# Review3 runtime follow-up 22 October 2026

Status: OPEN. These observations come from the local Review3 Gateway environment on
22 October 2026. They are not a release acceptance result.

## Core reference permissions — Hoàng Anh

Mobile now resolves permitted patient, staff, doctor and department references to
names. LAB_TECH, PHARMACIST, CASHIER and PATIENT still have restricted reference
lookup contracts. Mobile explicitly explains unavailable lookup and keeps raw
identifiers outside normal detail views. Provide a minimal reference directory
with role and context scope; do not grant broad PatientDTO access for labels.
Continue the existing [reference directory handoff](HANDOFF-HA-FRONTEND-REFERENCE-DIRECTORY-2026-10-22.md).

Acceptance: authorized workflow cards show names, unauthorized identities remain
protected, and no client infers identity from JWT subject or queries another
context database.

## Web Pharmacy identity labels — Huy

The existing web prescription detail still displays patient, prescriber, department
and record UUID references. Mobile uses permitted reference lookups and an explicit
unavailable-name message; technical references are collapsed. Apply the equivalent
presentation to the web feature after the reference contract is available.

Acceptance: normal prescription and dispense views use names or a clear permission
message; raw references are confined to deliberately expanded technical details.

## Lab billing reconciliation — Vinh

The seeded CBC requests can display READY and a prior clearance confirmation, while
the actual start command returns `LAB_CLEARANCE_REQUIRED` with
`Billing no longer authorizes this exact Lab test`. This was reproduced through
both web and mobile against rebuilt backend applications. The client must preserve
the rejection and reload authoritative state; never treat the old flag as authority.

Acceptance: rehearse a fresh request, exact Billing source lookup, payment, Lab
clearance publication, start and results through Gateway. Explain or reconcile old
seeded clearance using supported workflows. Do not patch another context database
or bypass the command check. Review3 captures the observed rejection, not successful
result entry.

## Report projection coverage — Huy

The outpatient daily report for 2026-10-22 returns `REPORT_NOT_FOUND` and
`Requested projection does not have accepted coverage`. Monthly report queries can
return a valid empty/zero result; that does not establish outpatient coverage.

Acceptance: obtain accepted projection coverage using the documented publication
and recovery workflow, then verify the daily query through Gateway. Preserve
unavailable coverage as an error until the service accepts it; do not fabricate
report values. Review3 screenshots describe the actual coverage error.

25 October Pharmacy/Report UI recheck: the local Gateway lists three prescriptions
for the demo patient while the legacy current-month Report returns zero-filled
details. Legacy HTTP 200 is not coverage acceptance. The web legacy workspace now
warns users explicitly and directs them to the source workflow for cross-checking;
the outpatient gate remains closed. The task-authorized minimal patient directory
now resolves patient names for PHARMACIST, but doctor/department reference access
and accepted outpatient Report coverage remain open dependencies above.
