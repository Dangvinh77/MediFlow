# Vinh outpatient acceptance implementation plan

**Goal:** Finish independent Clinical/Lab acceptance work and leave actionable owner handoffs for the remaining public MVP path. User approved continuation of the three packages proposed in the preceding source assessment.

**Architecture:** Keep production ownership in Clinical/Lab. Exercise existing packaged foreign applications through HTTP and RabbitMQ in disposable test environments; never import foreign domain classes or add production cross-context SQL. Test-only owned-store inspection is allowed for assertions. Use existing canonical fixtures and actual producer output, never invented cross-context relationships. Gates remain OFF by default.

**Tech stack:** Java 21, Maven Failsafe opt-in profiles, PostgreSQL 16, RabbitMQ 3.13, JUnit/Testcontainers, HTTP/JWT contracts already shipped.

## Task 1 — Clinical EXAM slice

**Source-confirmed scope adjustment:** Billing creates random V1 payment request IDs but exposes no source-bound discovery contract. A normal paid EXAM/LAB_TEST slice cannot be driven through HTTP without reading a foreign database or inventing an identifier. H-VINH-03 assigns that missing contract to Lộc. Implement the independent packaged Core/Gateway/Clinical authority slice now; label a canonical fixture grant explicitly as synthetic contract evidence. Keep actual payment acceptance open.

- [x] Lock live Core/Gateway/Billing/Clinical controllers, credentials, catalog and payment request contracts; missing request discovery is now H-VINH-03.
- [x] Add opt-in packaged Core/Gateway/Clinical acceptance: actual identity creation/login, check-in race, unpaid denial, wrong actor/role, canonical fixture grant, then one exam record. Targeted packaged run: 1 test, 0 failures/errors/skips. The original selected-payment/actual Billing grant objective remains blocked, separately tracked by H-VINH-03.
- [x] Fix the owned production defect exposed by this test: default JSON tree parsing rounded numeric Instant nanoseconds and rejected valid flat events. A real-converter regression failed before the per-read BigDecimal parsing fix, then the focused class passed 4/4; regression covers all four legacy event types and exact original bytes. No foreign producer bytes were rewritten.

## Task 2 — Lab/result slice

- [x] Add opt-in packaged Core/Clinical/Lab acceptance using actual Clinical order authority: request -> unpaid denial -> canonical fixture clearance -> start -> immutable result -> actual published Clinical attachment. Use the existing audited Clinical emergency override only to establish the prerequisite OPEN exam; label this explicitly. The original Billing normal-payment objective remains blocked by H-VINH-03.
- [x] Cover source mismatch, unpaid denial and duplicate/terminal behavior, including broker drain/no-DLQ after duplicate grant. Public Gateway Lab-order command remains an owner blocker until its exact route exists.
- [x] Run the test and retain reproducible command, source/flags and limits of evidence in the handoff. Final Lab packaged run: 1 test, 0 failures/errors/skips, including Windows TempDir cleanup; full Lab process-recovery verify: 216 regular + 2 recovery tests, all passing.

## Task 3 — Consumer crash recovery

- [x] Extend process acceptance to stop an actual consumer JVM after committed business/inbox effect and before ACK using test-only delivery control. Restart and assert exact delivery replay creates no second effect. Clinical and Lab targeted runs each passed 1 test, 0 failures/errors/skips; independent review found no issues. Full regression remains in Task 4.
- [x] Record the precise missing Billing discovery contract in H-VINH-03 rather than weakening normal-payment assertions or inspecting foreign business tables.

## Task 4 — Review, regression and handoffs

- [x] Review diff for ownership, real effects and gate defaults; run Clinical/Lab regression and opt-in acceptance profiles. Independent static review: no findings. Final Clinical verify exited 0 after bounded Windows log-release cleanup correction; Lab regression/recovery and packaged slice also exited 0.
- [x] Update `docs/handoffs/HANDOFF-VINH-OUTPATIENT-MVP.md`, registry and M6 plan: separate achieved local/packaged evidence from open full Gateway/Core/Rx/Report acceptance.
- [x] Record the required owner deliverables (not owner acceptance): Huy's outpatient Rx relationship bytes and Report proof, Hoang Anh's exact Lab-order route/Core migration/profile, and Loc's source-bound request discovery/catalog/payment/grant coordination, each with exact acceptance criteria in the registered handoff.
- [x] Report actual pass/fail/skip counts: Clinical 311 regular + 3 integration; Lab 216 regular + 2 recovery + 1 packaged = 533 tests, 0 failures/errors/skips. Do not claim backend DONE while producer contracts or full acceptance remain open; H-VINH-01/02/03 remain owner dependencies.
