# Active frontend handoffs

This registry contains only active frontend-to-frontend ownership, composition, or shared-scope
dependencies. Missing backend endpoints, DTOs, roles, events, identity claims, or Gateway contracts
belong in [`docs/handoffs/README.md`](../../../docs/handoffs/README.md).

## Current handoffs — 26/10/2026

| Handoff | Status | Producer → consumer | Acceptance còn cần đối chiếu |
|---|---|---|---|
| [Reference display names](HANDOFF-IDENTITY-CONSUMERS-DISPLAY-NAMES-REVIEW3.md) | NEEDS REVALIDATION | Hoàng Anh/Core contracts + Vinh/shared → owner từng feature | Có implementation/checkpoint mới hơn 22/10; đối chiếu exact role/route, signed browser, session reset và ảnh mới; backend gap theo H-01 |

Không giao viết lại selector chỉ từ mô tả ACTIVE cũ. Frontend Billing/Notification vẫn theo
canonical ownership/delegation; backend takeover không tự đổi người sửa frontend.

## Deferred, không chặn MVP

[Surgery idempotency consumer](../../../docs/handoffs/deferred/frontend-surgery-idempotency.md)
đã chuyển sang deferred. Không coi shared wrapper đã code là Surgery consumer đã nghiệm thu.
Không mở task Surgery để hoàn thiện lịch khám/ngoại trú.

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
