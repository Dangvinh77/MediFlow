@AGENTS.md

# Claude Code — MediFlow

<!-- Everything above comes from AGENTS.md, the cross-tool standard. Only Claude-specific
     content belongs below this line. Do not duplicate project rules here. -->

The shared instructions are imported from `AGENTS.md` above. What follows is Claude-only.

## Path-scoped rules

`.claude/rules/` holds rules that load **only** when you touch matching files:

- `frontend.md` → `frontend/**` — the Next.js rules, so backend sessions don't carry them.

There is also a nested `frontend/CLAUDE.md` (which imports `frontend/AGENTS.md`). Note that nested
CLAUDE.md files are **not** re-injected after `/compact`; the path-scoped rule is the reliable one.

## Codebase memory (codebase-memory-mcp) — optional

Configured in `.mcp.json`, but it is a native binary each developer installs themselves, so it is
often absent.

- **If `mcp__codebase-memory-mcp__*` tools are present:** prefer them for code exploration —
  `search_graph`, `trace_path`, `get_code_snippet`, `query_graph`, `get_architecture`, `search_code`.
  If the repo isn't indexed yet, run `index_repository` (or `/index-codebase`) first.
- **If they are absent:** use Grep/Glob/Read. Everything works, just slower. Do not tell the user to
  run `/index-codebase` — point them at `README.md` → Codebase Memory for the install link.

## Vendored tooling

- Agents: `.claude/agents/` — `spring-boot-engineer`, `java-architect`, `code-reviewer`, `test-engineer` (all backend).
- Commands: `.claude/commands/` — `/new-service`, `/index-codebase`, `/review-pr`.
- Skill: `.claude/skills/new-microservice/`.
- Shared settings: `.claude/settings.json` (committed). Machine-specific: `.claude/settings.local.json` (gitignored).
