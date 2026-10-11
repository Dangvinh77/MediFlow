# MediFlow Review 1 — Frontend Task Sheet

- **Plan preparation date:** 2026-10-12
- **Source baseline:** `64242496`
- **Scope:** Current frontend delivery and future frontend allocation by bounded-context ownership

## Evidence and Scheduling Rules

- Existing work is derived from the live Next.js routes, feature folders, frontend workboard, and service guides.
- `Actual Start Date` is the earliest Git author date for the relevant frontend context.
- `Actual Days` is the number of distinct Git author dates that touched the relevant feature and route paths; it is not elapsed calendar time.
- When no separate historical activity-plan date is traceable, a delivered row uses its earliest Git activity date as both planned and actual start evidence.
- Dates for future work are planning targets beginning on or after 2026-10-12. They do not claim that a missing backend contract already exists.
- Planned page names are proposals within the assigned owner's context and are explicitly prefixed with `Planned:`.

## Status Legend

| Status | Meaning |
|---|---|
| `DONE` | The route and stated workflow exist in the current source. |
| `IN PROGRESS` | A bounded implementation exists, but the complete workflow is not finished. |
| `PLANNED` | Future frontend work with a defined owner and no known blocking contract at preparation time. |
| `VERIFY CONTRACT` | The live controller, DTO, role, state, or error contract must be reconfirmed before implementation. |
| `BLOCKED` | A named backend, identity, Gateway, or producer contract is not yet available or activated. |

## Task Sheet

<table>
  <thead>
    <tr>
      <th rowspan="2">No.</th>
      <th rowspan="2">Member</th>
      <th rowspan="2">Contents</th>
      <th rowspan="2">Page Name</th>
      <th>Activity Plan</th>
      <th colspan="3">Plan Preparation Date</th>
    </tr>
    <tr>
      <th>Planned Start Date</th>
      <th>Actual Start Date</th>
      <th>Actual Days</th>
      <th>Status</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>1</td>
      <td>Vinh<br><code>Dangvinh77 / Harori</code></td>
      <td>Appointment list, filters, detail, create, edit, arrival, and cancellation with role and state guards.</td>
      <td><code>/appointments</code><br><code>/appointments/new</code><br><code>/appointments/[appointmentId]</code><br><code>/appointments/[appointmentId]/edit</code></td>
      <td>2026-07-28</td><td>2026-07-28</td><td>7</td><td><code>DONE</code></td>
    </tr>
    <tr>
      <td>2</td>
      <td>Vinh<br><code>Dangvinh77 / Harori</code></td>
      <td>Medical-record lookup, detail, create, symptom update, diagnosis entry, disposition, and immutable completed-state presentation.</td>
      <td><code>/records</code><br><code>/records/new</code><br><code>/records/[recordId]</code><br><code>/records/[recordId]/edit</code></td>
      <td>2026-07-28</td><td>2026-07-28</td><td>7</td><td><code>DONE</code></td>
    </tr>
    <tr>
      <td>3</td>
      <td>Vinh<br><code>Dangvinh77 / Harori</code></td>
      <td>Laboratory queue, filters, detail, request creation, specimen processing start, result entry, and cancellation.</td>
      <td><code>/lab</code><br><code>/lab/new</code><br><code>/lab/[testId]</code></td>
      <td>2026-07-28</td><td>2026-07-28</td><td>8</td><td><code>DONE</code></td>
    </tr>
    <tr>
      <td>4</td>
      <td>Vinh<br><code>Dangvinh77 / Harori</code></td>
      <td>Inpatient admission list/detail, admission creation, and bed list with exact Vietnamese wire names. Only the admission-create command slice is currently delivered.</td>
      <td><code>/inpatient</code><br><code>/inpatient/new</code><br><code>/inpatient/[admissionId]</code><br><code>/inpatient/beds</code></td>
      <td>2026-10-08</td><td>2026-10-08</td><td>2</td><td><code>IN PROGRESS</code></td>
    </tr>
    <tr>
      <td>5</td>
      <td>Vinh<br><code>Dangvinh77 / Harori</code></td>
      <td>SHARED testing work: add focused Clinical, Laboratory, and Inpatient API/component tests for role denial, validation, terminal states, retry, and correlation-ID rendering after the shared harness is available.</td>
      <td>Test suites for <code>/appointments</code>, <code>/records</code>, <code>/lab</code>, and <code>/inpatient</code></td>
      <td>2026-10-12</td><td>—</td><td>—</td><td><code>PLANNED</code></td>
    </tr>
    <tr>
      <td>6</td>
      <td>Vinh<br><code>Dangvinh77 / Harori</code></td>
      <td>Complete Inpatient bed assignment, transfer, and release controls after reconfirming controller roles, request DTOs, transition preconditions, and conflict codes.</td>
      <td><code>/inpatient/[admissionId]</code><br><code>/inpatient/beds</code> (future controls)</td>
      <td>2026-10-15</td><td>—</td><td>—</td><td><code>VERIFY CONTRACT</code></td>
    </tr>
    <tr>
      <td>7</td>
      <td>Vinh<br><code>Dangvinh77 / Harori</code></td>
      <td>Expose Clinical check-in, examination start, record completion, and admission-referral commands only after their backend and Gateway contracts are activated and smoke-tested.</td>
      <td><code>/appointments/[appointmentId]</code><br><code>/records/[recordId]</code> (future controls)</td>
      <td>2026-10-19</td><td>—</td><td>—</td><td><code>BLOCKED</code></td>
    </tr>
    <tr>
      <td>8</td>
      <td>Vinh<br><code>Dangvinh77 / Harori</code></td>
      <td>Add the explicit Clinical-to-Laboratory test-request handoff using test code and producer-generated order ID after the live producer/consumer contract is available.</td>
      <td>Planned: <code>/records/[recordId]/lab-orders</code></td>
      <td>2026-10-22</td><td>—</td><td>—</td><td><code>BLOCKED</code></td>
    </tr>
    <tr>
      <td>9</td>
      <td>Vinh<br><code>Dangvinh77 / Harori</code></td>
      <td>Add Inpatient treatment observations, bed transfer history, medical discharge, and administrative-close presentation after validating each route contract.</td>
      <td>Planned: <code>/inpatient/[admissionId]/care</code></td>
      <td>2026-10-26</td><td>—</td><td>—</td><td><code>VERIFY CONTRACT</code></td>
    </tr>
    <tr>
      <td>10</td>
      <td>Vinh<br><code>Dangvinh77 / Harori</code></td>
      <td>Integrate Inpatient deposit, top-up, settlement, Pharmacy, Surgery, Report, and Notification timeline data after the remaining handoffs are approved and same-byte producer/consumer fixtures pass.</td>
      <td><code>/inpatient/[admissionId]</code> (future integrated timeline)</td>
      <td>2026-10-29</td><td>—</td><td>—</td><td><code>BLOCKED</code></td>
    </tr>

    <tr>
      <td>11</td>
      <td>Huy<br><code>LQHuy0210</code></td>
      <td>Pharmacy drug catalogue/detail/create, stock adjustment, prescription create/lookup/detail, dispense/cancel, and administrator outbox replay.</td>
      <td><code>/pharmacy</code><br><code>/pharmacy/drugs</code><br><code>/pharmacy/prescriptions</code><br><code>/pharmacy/admin/outbox</code></td>
      <td>2026-07-28</td><td>2026-07-28</td><td>5</td><td><code>DONE</code></td>
    </tr>
    <tr>
      <td>12</td>
      <td>Huy<br><code>LQHuy0210</code></td>
      <td>Daily metrics, monthly revenue with daily detail, and top-dispensed medicine reports with validation, zero states, errors, and retry.</td>
      <td><code>/reports</code></td>
      <td>2026-07-28</td><td>2026-07-28</td><td>3</td><td><code>DONE</code></td>
    </tr>
    <tr>
      <td>13</td>
      <td>Huy<br><code>LQHuy0210</code></td>
      <td>SHARED testing work: add Pharmacy validation, permission, terminal-state, API-error, and replay tests after the common test harness is available.</td>
      <td>Test suite for <code>/pharmacy</code></td>
      <td>2026-10-12</td><td>—</td><td>—</td><td><code>PLANNED</code></td>
    </tr>
    <tr>
      <td>14</td>
      <td>Huy<br><code>LQHuy0210</code></td>
      <td>SHARED testing work: add Report formatter, filter, permission, zero-value, error, and retry tests after the common test harness is available.</td>
      <td>Test suite for <code>/reports</code></td>
      <td>2026-10-14</td><td>—</td><td>—</td><td><code>PLANNED</code></td>
    </tr>
    <tr>
      <td>15</td>
      <td>Huy<br><code>LQHuy0210</code></td>
      <td>Add operational, Inpatient, and Surgery snapshots after verifying the available conditional Report controllers, DTOs, roles, activation settings, and Gateway routing.</td>
      <td>Planned: <code>/reports/operations</code></td>
      <td>2026-10-16</td><td>—</td><td>—</td><td><code>VERIFY CONTRACT</code></td>
    </tr>
    <tr>
      <td>16</td>
      <td>Huy<br><code>LQHuy0210</code></td>
      <td>Add administrator financial source-archive export/restore after verifying the available conditional binary contract, size limit, authorization, activation settings, and Gateway behavior; do not present it as an ordinary report export.</td>
      <td>Planned: <code>/reports/financial-archives</code></td>
      <td>2026-10-19</td><td>—</td><td>—</td><td><code>VERIFY CONTRACT</code></td>
    </tr>
    <tr>
      <td>17</td>
      <td>Huy<br><code>LQHuy0210</code></td>
      <td>Create the Surgery case list and detail workspace after the Surgery backend route, authority handoff, DTOs, roles, and Gateway routing are approved.</td>
      <td>Planned: <code>/surgery</code><br>Planned: <code>/surgery/[caseId]</code></td>
      <td>2026-10-22</td><td>—</td><td>—</td><td><code>BLOCKED</code></td>
    </tr>
    <tr>
      <td>18</td>
      <td>Huy<br><code>LQHuy0210</code></td>
      <td>Add Surgery request, readiness, clearance, schedule, execution, recovery, outcome, cancellation, and charge-handoff controls after each live command contract is verified.</td>
      <td>Planned: <code>/surgery/[caseId]/workflow</code></td>
      <td>2026-10-26</td><td>—</td><td>—</td><td><code>BLOCKED</code></td>
    </tr>
    <tr>
      <td>19</td>
      <td>Huy<br><code>LQHuy0210</code></td>
      <td>Add Surgery permission, lifecycle, conflict, retry, and integration tests after the Surgery workspace and approved fixtures exist.</td>
      <td>Planned Surgery test suite</td>
      <td>2026-10-29</td><td>—</td><td>—</td><td><code>BLOCKED</code></td>
    </tr>

    <tr>
      <td>20</td>
      <td>Hoàng Anh<br><code>TranHoangAnh94</code></td>
      <td>Organization department and staff list/detail/create/update, staff transfer, and account create/lock/unlock administration.</td>
      <td><code>/organization</code><br><code>/organization/departments/new</code><br><code>/organization/departments/[departmentId]</code><br><code>/organization/departments/[departmentId]/edit</code><br><code>/organization/staff/new</code><br><code>/organization/staff/[staffId]</code><br><code>/organization/staff/[staffId]/edit</code><br><code>/organization/accounts</code></td>
      <td>2026-07-28</td><td>2026-07-28</td><td>3</td><td><code>DONE</code></td>
    </tr>
    <tr>
      <td>21</td>
      <td>Hoàng Anh<br><code>TranHoangAnh94</code></td>
      <td>Patient paged search, detail, create, update, delete, exact Vietnamese wire mapping, and immutable identity-number behavior.</td>
      <td><code>/patients</code><br><code>/patients/new</code><br><code>/patients/[patientId]</code><br><code>/patients/[patientId]/edit</code></td>
      <td>2026-07-28</td><td>2026-07-28</td><td>6</td><td><code>DONE</code></td>
    </tr>
    <tr>
      <td>22</td>
      <td>Hoàng Anh<br><code>TranHoangAnh94</code></td>
      <td>SHARED testing work: add Organization partial-failure, pagination, empty-state, role-gate, account, and retry tests after the common harness is available.</td>
      <td>Test suite for <code>/organization</code></td>
      <td>2026-10-12</td><td>—</td><td>—</td><td><code>PLANNED</code></td>
    </tr>
    <tr>
      <td>23</td>
      <td>Hoàng Anh<br><code>TranHoangAnh94</code></td>
      <td>SHARED testing work: add Patient contract, validation, permission, deletion, empty/error, and retry tests after the common harness is available.</td>
      <td>Test suite for <code>/patients</code></td>
      <td>2026-10-14</td><td>—</td><td>—</td><td><code>PLANNED</code></td>
    </tr>
    <tr>
      <td>24</td>
      <td>Hoàng Anh<br><code>TranHoangAnh94</code></td>
      <td>Provide authorized patient, doctor, and department lookup contracts for human-facing selectors used by Clinical, Laboratory, Billing, and Report; consumers must not use service-only endpoints.</td>
      <td>Shared consumer forms in <code>/appointments</code>, <code>/lab</code>, <code>/billing</code>, and <code>/reports</code></td>
      <td>2026-10-16</td><td>—</td><td>—</td><td><code>VERIFY CONTRACT</code></td>
    </tr>
    <tr>
      <td>25</td>
      <td>Hoàng Anh<br><code>TranHoangAnh94</code></td>
      <td>Define and integrate stable authenticated <code>staffId</code>, patient identity, and account-to-subject mapping across Gateway and Organization without inferring undocumented token claims.</td>
      <td>Shared: <code>/login</code> and session integration</td>
      <td>2026-10-19</td><td>—</td><td>—</td><td><code>VERIFY CONTRACT</code></td>
    </tr>
    <tr>
      <td>26</td>
      <td>Hoàng Anh<br><code>TranHoangAnh94</code></td>
      <td>Add secure patient self-service for the authenticated patient's own profile after Gateway identity and Patient ownership/mutation contracts are approved.</td>
      <td>Planned: <code>/patients/me</code></td>
      <td>2026-10-22</td><td>—</td><td>—</td><td><code>BLOCKED</code></td>
    </tr>
    <tr>
      <td>27</td>
      <td>Hoàng Anh<br><code>TranHoangAnh94</code></td>
      <td>Provide the identity handoff required for patient-owned appointments, results, prescriptions, invoices, receipts, payments, and notifications; each consumer retains its own UI ownership.</td>
      <td>Planned identity contract for patient portal routes</td>
      <td>2026-10-26</td><td>—</td><td>—</td><td><code>BLOCKED</code></td>
    </tr>

    <tr>
      <td>28</td>
      <td>Lộc<br><code>locgit-89</code></td>
      <td>Billing patient invoice lookup, invoice detail, invoice creation from server-owned fees, payment, receipt state, and fee breakdown without client-side authoritative totals.</td>
      <td><code>/billing</code><br><code>/billing/new</code><br><code>/billing/[invoiceId]</code></td>
      <td>2026-07-28</td><td>2026-07-28</td><td>3</td><td><code>DONE</code></td>
    </tr>
    <tr>
      <td>29</td>
      <td>Lộc<br><code>locgit-89</code></td>
      <td>Notification patient history, privacy-safe secured detail, and administrator manual-send workflow.</td>
      <td><code>/notifications</code><br><code>/notifications/[notificationId]</code><br><code>/notifications/send</code></td>
      <td>2026-07-28</td><td>2026-07-28</td><td>3</td><td><code>DONE</code></td>
    </tr>
    <tr>
      <td>30</td>
      <td>Lộc<br><code>locgit-89</code></td>
      <td>SHARED testing work: add Billing pagination, monetary display, permission, terminal-state, API-error, retry, and payment-flow tests after the common harness is available.</td>
      <td>Test suite for <code>/billing</code></td>
      <td>2026-10-12</td><td>—</td><td>—</td><td><code>PLANNED</code></td>
    </tr>
    <tr>
      <td>31</td>
      <td>Lộc<br><code>locgit-89</code></td>
      <td>SHARED testing work: add Notification pagination, channel/status, privacy-boundary, cross-patient denial, error, and retry tests after the common harness is available.</td>
      <td>Test suite for <code>/notifications</code></td>
      <td>2026-10-14</td><td>—</td><td>—</td><td><code>PLANNED</code></td>
    </tr>
    <tr>
      <td>32</td>
      <td>Lộc<br><code>locgit-89</code></td>
      <td>Add ledger payment/refund, admission deposit allocation, settlement preview, reconciliation, settlement, and final-settlement controls after verifying the available conditional Billing controllers, DTOs, idempotency headers, roles, activation settings, and Gateway routing.</td>
      <td>Planned: <code>/billing/accounts/[accountId]</code><br>Planned: <code>/billing/transactions/[transactionId]</code></td>
      <td>2026-10-16</td><td>—</td><td>—</td><td><code>VERIFY CONTRACT</code></td>
    </tr>
    <tr>
      <td>33</td>
      <td>Lộc<br><code>locgit-89</code></td>
      <td>Add notification read-state and authorized retry commands only after the live Notification contract defines endpoints, roles, lifecycle rules, and privacy-safe responses.</td>
      <td><code>/notifications/[notificationId]</code> (future controls)</td>
      <td>2026-10-19</td><td>—</td><td>—</td><td><code>BLOCKED</code></td>
    </tr>
    <tr>
      <td>34</td>
      <td>Lộc<br><code>locgit-89</code></td>
      <td>Add authenticated “My Notifications” after Hoàng Anh's Gateway/Patient identity mapping is available and backend ownership checks are enforced.</td>
      <td>Planned: <code>/notifications/me</code></td>
      <td>2026-10-22</td><td>—</td><td>—</td><td><code>BLOCKED</code></td>
    </tr>
    <tr>
      <td>35</td>
      <td>Lộc<br><code>locgit-89</code></td>
      <td>Define and expose currency semantics for Billing displays in coordination with Report; the frontend must not infer currency from locale.</td>
      <td><code>/billing</code> and shared display contract for <code>/reports</code></td>
      <td>2026-10-26</td><td>—</td><td>—</td><td><code>VERIFY CONTRACT</code></td>
    </tr>
    <tr>
      <td>36</td>
      <td>Lộc<br><code>locgit-89</code></td>
      <td>Run authenticated Gateway smoke tests for Billing and Notification roles, privacy boundaries, error correlation IDs, and completed finance/notification workflows.</td>
      <td>Release verification for <code>/billing</code> and <code>/notifications</code></td>
      <td>2026-10-29</td><td>—</td><td>—</td><td><code>PLANNED</code></td>
    </tr>
  </tbody>
</table>

## Ownership and Dependency Notes

1. Vinh owns Clinical, Laboratory, and Inpatient frontend contexts. Cross-context identifiers remain bare references and must come from approved contracts.
2. Huy owns Pharmacy, Report, and the planned Surgery frontend. Surgery remains future work because no frontend route is present in the current source.
3. Hoàng Anh owns Organization, Patient, and the Gateway-facing identity liaison lane. Shared login/session changes still require explicit shared scope.
4. Lộc owns Billing and Notification. Financial totals, clearance, retry, and privacy decisions remain backend-authoritative.
5. Shared components, session files, API wrappers, configuration, and the common test harness are not silently assigned by this table; rows marked `SHARED` identify coordination work only.
