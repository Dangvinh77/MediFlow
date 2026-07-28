@AGENTS.md

# Claude Code — MediFlow

<!-- Everything above comes from AGENTS.md, the cross-tool standard. Only Claude-specific
     content belongs below this line. Do not duplicate project rules here. -->

The shared instructions are imported from `AGENTS.md` above. What follows is Claude-only.

## Path-scoped rules

`.claude/rules/` holds rules that load **only** when you touch matching files:

- `frontend.md` → `frontend/**` — the Next.js rules, so backend sessions don't carry them.
- `flutter.md` → `mobile/**` — the Flutter rules.

There is also a nested `frontend/CLAUDE.md` (which imports `frontend/AGENTS.md`). Note that nested
CLAUDE.md files are **not** re-injected after `/compact`; the path-scoped rule is the reliable one.

## Optional AI codebase tools

Two optional tools for code exploration. Full docs: [`docs/ai/13-codebase-tools.md`](docs/ai/13-codebase-tools.md).

### codebase-memory-mcp (graph MCP for agents)

Configured in `.mcp.json`, but the native binary must be installed first (see setup scripts).

- **If `mcp__codebase-memory-mcp__*` tools are present:** prefer them — `search_graph`, `trace_path`,
  `get_code_snippet`, `query_graph`, `get_architecture`, `search_code`. If not indexed yet, run
  `/index-codebase`.
- **If they are absent:** use Grep/Glob/Read. Everything works, just slower.
- Setup: `scripts\setup-codebase-memory.bat` (Windows) or `scripts/setup-codebase-memory.sh` (macOS/Linux).

### Understand-Anything (visual dashboard for humans)

A Claude Code plugin for interactive codebase exploration. Installed via:
```
/plugin marketplace add Egonex-AI/Understand-Anything
/plugin install understand-anything
```
Or run `scripts\setup-understand-anything.bat` / `scripts/setup-understand-anything.sh`.

- **If `/understand` is available:** use it to explore beyond what graph tools can tell you.
- **If absent:** no problem — everything works with Grep/Glob/Read.

## Vendored tooling

- Agents: `.claude/agents/` — `spring-boot-engineer`, `java-architect`, `code-reviewer`, `test-engineer` (all backend).
- Commands: `.claude/commands/` — `/new-service`, `/index-codebase`, `/review-pr`.
- Skill: `.claude/skills/new-microservice/`.
- Shared settings: `.claude/settings.json` (committed). Machine-specific: `.claude/settings.local.json` (gitignored).
