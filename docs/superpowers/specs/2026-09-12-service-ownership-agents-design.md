# Service Ownership AGENTS Design

## Goal

Prevent coding agents from changing production code owned by another developer while preserving
read-only contract investigation and documentation handoffs. Ownership rules must work for parent
agents and every subagent without introducing a pull-request approval requirement.

## Ownership map

| Owner | Git identity | Owned backend modules |
|---|---|---|
| Vinh | `Dangvinh77` / `Harori` | `clinical-service`, `lab-service` |
| Huy | `LQHuy0210` | `pharmacy-service`, `report-service` |
| Hoàng Anh | `TranHoangAnh94` | `organization-service`, `patient-service`, `gateway` |
| Lộc | `locgit-89` | `billing-service`, `notification-service` |

The ownership map follows the approved Stage 3 assignment in
`docs/monitor_proj_progress/03-giai-doan-3-phan-chia-hoan-thien-ho-so/giai-doan-3-final-review.md`.

## Instruction hierarchy

The root `AGENTS.md` will define the ownership policy that applies across the repository. Each owned
backend module will receive a nested `AGENTS.md` containing only its owner, writable scope, known
integration boundaries, and local verification command. Nested files will explicitly preserve the
root architecture, security, testing, and Git rules; they refine ownership rather than replacing
those standards.

The implementation will add these nested files:

- `backend/clinical-service/AGENTS.md`
- `backend/lab-service/AGENTS.md`
- `backend/pharmacy-service/AGENTS.md`
- `backend/report-service/AGENTS.md`
- `backend/organization-service/AGENTS.md`
- `backend/patient-service/AGENTS.md`
- `backend/gateway/AGENTS.md`
- `backend/billing-service/AGENTS.md`
- `backend/notification-service/AGENTS.md`

## Write boundary

An owner may change production code, tests, migrations, module configuration, and module-local API
examples inside their assigned module. Reading any other module is allowed when needed to verify an
API or event contract.

An agent must not change another owner's production source, tests, database migrations, module
configuration, or module-local documentation as an indirect way to unblock its own service. Missing
cross-service work becomes a handoff document under `docs/`, containing:

1. producer and owner;
2. consumer;
3. required endpoint or event;
4. exact required fields and semantics;
5. why the consumer needs the contract;
6. acceptance criteria and compatibility expectations.

The blocked implementation remains blocked until the producing owner supplies the contract. Agents
must not infer identifiers, query another service's database, introduce temporary cross-service
coupling, or edit the producer on the consumer owner's behalf.

## Shared areas and overrides

`backend/common`, `backend/eureka-server`, root build files, `.github`, and repository-wide scripts
are shared integration areas. They may be changed only when the task explicitly assigns that shared
change. Gateway remains owned by Hoàng Anh.

Vinh's team-lead role permits review, integration coordination, merging, and documentation across
the repository. It does not silently expand Harori's production write scope beyond Clinical and Lab.
Any temporary ownership override must be explicit in the user task and name the additional module
or shared path. The override applies only to that task.

## Agent and subagent behavior

The active developer identity comes from the explicit task context. Agents must not infer authority
from a shared machine's Git configuration. When the active developer is unclear, agents may inspect
code and write a handoff, but must not modify production code until the task identifies the owner or
grants an explicit override.

Every subagent inherits the parent's ownership boundary. A parent may delegate read-only contract
investigation outside its scope, but it may not delegate unauthorized writes to bypass the rule.

## Backlog classification

Tasks inside the active owner's modules use `IMPLEMENT`. Tasks requiring another owner to change an
API, event, schema, or producer use `HANDOFF`. A handoff is complete when the contract document is
specific enough for the producing owner to implement and test independently.

## Enforcement and merge policy

`AGENTS.md` is an agent guardrail, not a GitHub authorization control. This change will not enable
required Code Owner reviews and will not alter the repository ruleset. Existing CI remains the merge
gate. A later custom CI ownership check can be added separately if the team wants hard path-level
enforcement without human approval.

## Verification

The implementation will verify:

- all nine nested files exist and name the correct owner;
- each nested file preserves the root standards;
- every file includes the external-service read-only and handoff rules;
- every file states that subagents inherit the same boundary;
- shared paths and explicit task-scoped overrides are unambiguous;
- no GitHub review or ruleset setting is changed.
