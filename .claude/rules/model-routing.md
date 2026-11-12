# Model routing and token discipline (Claude Code only, always loaded)

Use the cheapest model that reliably completes the task. Follow this silently for routine work;
do not print routing explanations. Engineering rules stay in `AGENTS.md` and `docs/ai/*`.

## Levels

| Level | Work | Where it runs |
|---|---|---|
| 0 Exploration | Locate files, symbols, endpoints, tests; summarize small configs | Inline for a trivial lookup; `fast-explorer` (Haiku 5.5, low) when the search spans modules and only the conclusion is needed |
| 1 Routine | Small UI tweaks, simple DTO/validation/CRUD edits, unit tests, fixtures, docs, boilerplate, mechanical refactors | `routine-coder` (Sonnet 5.5, medium) when the task is well scoped and independent; otherwise inline |
| 2 Core engineering | Spring Boot backend work, complex React/TypeScript logic, business workflows, authorization, transactions, integration debugging, contract changes, significant refactors | Main session (Opus 5.5, medium; high when justified). Existing `spring-boot-engineer`, `test-engineer`, `java-architect`, `code-reviewer` inherit the session model |
| 3 Advanced reasoning | Cross-service root cause with unresolved ambiguity, distributed transactions, concurrency/idempotency/consistency failures, high-consequence architecture or migration tradeoffs, problems Opus could not resolve | `fable-advisor` (Fable 5.2, high), read-only, user-approved only |

Escalate Level 1 to Level 2 as soon as the change touches a nontrivial business invariant,
a transaction, concurrency, or a cross-service contract.

## Fable escalation gate

Fable is an escalation model, never a routine one. Before invoking `fable-advisor`:

1. Confirm the relevant files and contracts were actually read.
2. Rule out missing context as the real problem.
3. Complete a focused Opus analysis first.
4. Judge that the likely benefit justifies the cost.
5. State the reason in one or two lines and get the user's explicit approval.

Fable returns diagnosis, evidence, risks and a recommendation. The main session implements and verifies.

## Token discipline

- Keep implementation and final integration in the main session when delegating would duplicate context. Delegation costs tokens; never spawn agents for the appearance of parallel work.
- Parallel agents only for genuinely independent tasks; default to sequential.
- Do not switch the main session's model back and forth.
- Targeted reads, grep and symbol search over whole-directory reads. Do not re-read unchanged large files or load every service for a local change.
- Return summaries with `path:line` references, not raw logs.
- Targeted tests first, then widen by impact. Never rerun an identical failing command without a new hypothesis.
- No long plans for trivial tasks, no unrequested documentation, no rewrites of unaffected modules.

## MediFlow guardrails

For clinical, lab, pharmacy, billing, patient, appointment and inpatient workflows, business
correctness outranks token savings:

- Preserve API and event contracts unless a change is explicitly required; verify producer and consumer sides when either changes.
- Check authorization and department boundaries, idempotency and transactional consistency.
- Check current source and docs before assuming which service owns a workflow.
- Security-sensitive and financial business-rule decisions are never left to Haiku or Sonnet alone; the main session reviews them.

## Reporting

Report the work actually performed. Never claim a specific model handled something unless Claude
Code confirms it (`/model`, `/status`, or the subagent's own definition).
