# 10 — Git Workflow

## Branches

- `main` — always buildable. Protected. No direct pushes.
- Feature: `feat/<service>-<short-desc>` e.g. `feat/patient-crud`.
- Fix: `fix/<service>-<short-desc>`. Chore/docs: `chore/...`, `docs/...`.
- One service / one concern per branch where possible (monorepo, but keep PRs focused).

## Commits (Conventional Commits)

```
<type>(<scope>): <subject>

<body — why, not what>
```

- `type`: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `build`, `ci`.
- `scope`: the service or area — `patient`, `billing`, `gateway`, `common`, `ai-rules`, `mcp`.
- Subject: imperative, ≤ 72 chars, English or Vietnamese (be consistent within the team).
- Example: `feat(pharmacy): dispense reduces stock and publishes prescription.filled`

## Pull requests

- PR description states: what changed, which design-doc section / business rules it implements, how it was tested.
- Checklist before requesting review:
  - [ ] Follows `docs/ai/04-microservice-blueprint.md` layout.
  - [ ] Business rules from the design doc implemented **and tested**.
  - [ ] Endpoints have role checks (`07`).
  - [ ] Events published/consumed match `06` + the service doc.
  - [ ] `mvn verify` green locally.
  - [ ] Re-indexed codebase memory if structure changed (see README > Codebase Memory).
- At least one human review. AI review (`/review-pr` command / `code-reviewer` agent) is encouraged but not a substitute.

## Keeping the framework in sync

- Changing a **coding rule** → edit `docs/ai/*` (never the tool entry files). One PR, scope `ai-rules`.
- Changing **tooling** (MCP, agents, hooks) → scope `mcp` / `tooling`, and update `README.md` setup steps if the change affects onboarding.

## After pulling

```bash
git pull
# rebuild if POMs changed
mvn -q -DskipTests install
# re-index codebase memory (Claude users) — see scripts/index-codebase.*
```
