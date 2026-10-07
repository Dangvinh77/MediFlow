# HANDOFF — Gateway deployment smoke for Clinical/Lab roles

**Status:** ACTIVE / CI RED — route-policy reconciliation is required before real downstream smoke.
**Owner:** Hoàng Anh (`TranHoangAnh94`), Gateway.
**Unblocks:** closing the Clinical/Lab routing handoff; it does not enable Care-Finance messaging.

## Producer

Gateway verifies JWTs, preserves correlation IDs, applies exact route-role rules and forwards only authorized requests to the discovered service.

## Consumer

Clinical and Lab enforce the same roles again with `@PreAuthorize`. Their controller policy is the downstream authority and must remain at least as restrictive as Gateway.

## Owner actions

### Current CI blocker

`Service integration` on [PR #301](https://github.com/Dangvinh77/MediFlow/pull/301) and the
documentation-only [PR #303](https://github.com/Dangvinh77/MediFlow/pull/303) both report the same
Gateway regression: 168 Gateway tests run with 13 failures.

- `CareRouteAuthorizationTest`: 12 failures show that the route matcher no longer agrees with the
  canonical allowed/denied role matrix, including an unknown nested command accepted unexpectedly.
- `GatewayRouteDefinitionTest.surgeryRoute_defaultFlag_hasNoRoute`: the default configuration
  exposes `/api/v1/surgery/**` although the test contract expects the route to stay disabled.

PR #303 changes no Java, YAML or Gateway file, so it did not introduce these failures. Hoàng Anh
must reconcile the route matcher and Surgery default flag with the intended policy, then make the
full Gateway suite green before deployment smoke.

After CI is green, run the real deployed Gateway-to-service smoke for these commands:

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

- `mvn -q -pl backend/gateway -am test` passes with zero failures.
- The repository `Service integration` check passes on the exact candidate commit.
- Each allowed role reaches the real controller through Gateway with a signed token.
- Each denied role receives 403 before a service-side state change.
- Nested unknown write paths remain denied.
- Correlation ID and authenticated subject/roles arrive unchanged downstream.
- Move the lasting matrix into Gateway/Clinical/Lab service docs, then delete this handoff and its registry row in the same PR.
