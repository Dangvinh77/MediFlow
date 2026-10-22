# Active frontend handoffs

This registry contains only active frontend-to-frontend ownership, composition, or shared-scope
dependencies. Missing backend endpoints, DTOs, roles, events, identity claims, or Gateway contracts
belong in [`docs/handoffs/README.md`](../../../docs/handoffs/README.md).

## Active handoffs

- [Review 3 — hiển thị tên bệnh nhân, bác sĩ và khoa thay UUID](HANDOFF-IDENTITY-CONSUMERS-DISPLAY-NAMES-REVIEW3.md): Hoàng Anh đối chiếu identity contract/quyền đọc; Vinh điều phối shared và các consumer nhận theo ownership. ACTIVE; Huy đã code web Pharmacy/Report và mobile Pharmacy, chờ nghiệm thu Gateway/ảnh mới. Consumer của owner khác vẫn theo tiến độ riêng.

| Handoff | Producer owner | Consumer owner | Unblocks |
|---|---|---|---|
| [Surgery idempotency headers](HANDOFF-SHARED-HUY-IDEMPOTENCY-HEADERS.md) | Vinh — shared API wrapper | Huy — Surgery | S-04 command dispatch through the existing HTTP boundary |

## When to create one

Create a handoff only when one authorized frontend scope cannot finish without a concrete action or
decision from another owner. A one-task explicit delegation does not need a handoff.

## Required template

Copy this template into `HANDOFF-<PRODUCER>-<CONSUMER>-<SLUG>.md` in this directory:

```markdown
# HANDOFF: <producer context> to <consumer context> — <outcome>

- Status: ACTIVE
- Producer owner: <human owner and Git identity>
- Consumer owner: <human owner and Git identity>
- Affected contexts: <exact contexts>
- Affected paths: <exact paths>
- Verified baseline: <commit and public contract>
- Required action or decision: <concrete missing result>
- Acceptance criteria: <observable completion conditions>
- Compatibility expectation: <what existing consumers must retain>
- Resume owner: <who continues after acceptance>
```

The angle-bracket fields are a copyable template, not unfinished requirements in this registry.

## Lifecycle

1. Register only active blockers.
2. Do not use a handoff as broad write permission.
3. Link a backend-contract handoff instead of duplicating it here.
4. After acceptance, move durable rules to canonical docs and delete the completed handoff entry and
   file in the same PR.
