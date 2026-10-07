# HANDOFF — Gateway deployment smoke for Clinical/Lab roles

**Status:** ACTIVE — role code and tests are complete; real downstream smoke is pending.
**Owner:** Hoàng Anh (`TranHoangAnh94`), Gateway.
**Unblocks:** closing the Clinical/Lab routing handoff; it does not enable Care-Finance messaging.

## Producer

Gateway verifies JWTs, preserves correlation IDs, applies exact route-role rules and forwards only authorized requests to the discovered service.

## Consumer

Clinical and Lab enforce the same roles again with `@PreAuthorize`. Their controller policy is the downstream authority and must remain at least as restrictive as Gateway.

## Owner actions

Run the real deployed Gateway-to-service smoke for these commands:

| Command | Allowed roles |
|---|---|
| Clinical appointment check-in | ADMIN, NURSE |
| Clinical examination start | ADMIN, DOCTOR |
| Clinical admission referral | ADMIN, DOCTOR |
| Lab list/search | ADMIN, MANAGER, DOCTOR, NURSE, LAB_TECH |
| Lab item read | ADMIN, DOCTOR, NURSE, LAB_TECH |
| Lab start | ADMIN, LAB_TECH |
| Lab cancel | ADMIN, DOCTOR, LAB_TECH |

Preserve exact path matching and default deny. Do not replace these rules with broad appointment, record or Lab write patterns.

## Acceptance criteria

- Each allowed role reaches the real controller through Gateway with a signed token.
- Each denied role receives 403 before a service-side state change.
- Nested unknown write paths remain denied.
- Correlation ID and authenticated subject/roles arrive unchanged downstream.
- Move the lasting matrix into Gateway/Clinical/Lab service docs, then delete this handoff and its registry row in the same PR.
