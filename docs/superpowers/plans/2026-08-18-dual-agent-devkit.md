# Dual-Agent Dev Kit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add committed Codex-native adapters that stay semantically synchronized with the existing Claude Code dev kit.

**Architecture:** `.claude` remains the Claude adapter and source material. A deterministic Node.js sync/audit script generates Codex agents, skills, and hooks into their native project locations, while hand-maintained shared configuration maps MCP and scoped instruction behavior that cannot be generated safely from Claude schemas.

**Tech Stack:** Node.js 20+, JSON, TOML, Markdown, PowerShell/Bash-compatible repository tooling, Claude Code configuration, Codex project configuration

---

### Task 1: Make shared MCP configuration portable

**Files:**
- Modify: `.mcp.json`
- Modify: `.claude/mcp-wrapper.bat`
- Modify: `.claude/mcp-wrapper.sh`
- Create: `.codex/config.toml`

- [ ] **Step 1: Replace the hard-coded Claude MCP executable path**

Set `.mcp.json` server `codebase-memory-mcp.command` to `codebase-memory-mcp`, retain its empty arguments/environment, and retain Mermaid Chart's HTTP URL.

- [ ] **Step 2: Make both Claude wrappers resolve the native binary from `PATH`**

Use this Windows wrapper behavior:

```bat
@echo off
where codebase-memory-mcp.exe >nul 2>&1
if errorlevel 1 (
  echo [FAIL] codebase-memory-mcp.exe is not on PATH. Run scripts\setup-codebase-memory.bat. 1>&2
  exit /b 1
)
codebase-memory-mcp.exe %*
```

Use this POSIX wrapper behavior:

```sh
#!/usr/bin/env sh
if ! command -v codebase-memory-mcp >/dev/null 2>&1; then
  echo "[FAIL] codebase-memory-mcp is not on PATH. Run scripts/setup-codebase-memory.sh." >&2
  exit 1
fi
exec codebase-memory-mcp "$@"
```

- [ ] **Step 3: Create project-scoped Codex configuration**

Create `.codex/config.toml` with hooks enabled and the same two MCP servers:

```toml
[features]
hooks = true

[mcp_servers."codebase-memory-mcp"]
command = "codebase-memory-mcp"
startup_timeout_sec = 30

[mcp_servers.mermaidchart]
url = "https://mcp.mermaid.ai/mcp"
```

- [ ] **Step 4: Validate portable configuration**

Parse `.mcp.json`, parse `.codex/config.toml` with Python `tomllib`, and confirm neither committed file contains `C:\Users\hp` or `C:\Users\VIP`.

- [ ] **Step 5: Commit portable MCP configuration**

Commit only the four task files using `chore(tooling): make agent MCP config portable`.

### Task 2: Generate Codex-native agents, commands, skills, and hooks

**Files:**
- Create: `scripts/sync-agent-devkit.mjs`
- Generate: `.codex/agents/code-reviewer.toml`
- Generate: `.codex/agents/java-architect.toml`
- Generate: `.codex/agents/spring-boot-engineer.toml`
- Generate: `.codex/agents/test-engineer.toml`
- Generate: `.agents/skills/index-codebase/SKILL.md`
- Generate: `.agents/skills/new-service/SKILL.md`
- Generate: `.agents/skills/review-pr/SKILL.md`
- Generate: `.agents/skills/new-microservice/SKILL.md`
- Regenerate: `.codex/hooks.json`

- [ ] **Step 1: Implement source discovery and frontmatter parsing**

The script must locate the Git root from its own file location, parse the simple YAML frontmatter used by `.claude/agents/*.md` and `.claude/commands/*.md`, and expose `--write` and `--check` modes. It must use only Node built-ins.

- [ ] **Step 2: Implement Codex agent generation**

For every `.claude/agents/*.md` file, generate `.codex/agents/<same-basename>.toml` containing:

```toml
name = "<hyphens converted to underscores>"
description = "<Claude description>"
sandbox_mode = "<read-only unless Claude tools include Write or Edit>"
developer_instructions = '''
<complete Claude agent body>
'''
```

Reject a source body containing `'''` instead of generating invalid TOML.

- [ ] **Step 3: Implement Claude-command to Codex-skill generation**

Generate one `.agents/skills/<command>/SKILL.md` per `.claude/commands/*.md`. Preserve the command description and body, add a valid `name`, remove unsupported `argument-hint`, and apply these compatibility rewrites:

- `$ARGUMENTS` → `<service-name>`;
- `.claude/skills/new-microservice/SKILL.md` → `.agents/skills/new-microservice/SKILL.md`;
- Claude agent references → corresponding `.codex/agents/*.toml` names;
- the statement that codebase-memory is Claude-only → a cross-tool optional-MCP fallback statement;
- fixed `git diff main...HEAD` → default-branch detection with `origin/HEAD`, falling back to `master` then `main`.

- [ ] **Step 4: Mirror the tool-agnostic microservice skill**

Copy `.claude/skills/new-microservice/SKILL.md` exactly to `.agents/skills/new-microservice/SKILL.md` so semantic equality can be checked byte-for-byte.

- [ ] **Step 5: Generate Codex hooks from Claude settings**

Read `.claude/settings.json`, extract its `hooks` object, and write `.codex/hooks.json` with the existing description and extracted hooks. The command arrays must remain exactly equal.

- [ ] **Step 6: Implement audit mode**

`node scripts/sync-agent-devkit.mjs --check` must fail with actionable messages when generated content differs, a counterpart is missing, MCP mappings disagree, nested rule entry points are missing, local settings become tracked, or known personal absolute paths appear in committed adapter files.

- [ ] **Step 7: Generate and validate all adapters**

Run:

```powershell
node scripts/sync-agent-devkit.mjs --write
node scripts/sync-agent-devkit.mjs --check
```

Expected: generation succeeds and audit prints a PASS summary for 4 agents, 4 skills, hooks, rules, and MCP mappings.

- [ ] **Step 8: Commit generator and generated adapters**

Commit only the task files using `feat(tooling): add Codex dev kit adapters`.

### Task 3: Add Codex-compatible scoped instructions

**Files:**
- Create: `mobile/AGENTS.md`
- Verify: `frontend/AGENTS.md`
- Verify: `AGENTS.md`

- [ ] **Step 1: Add the mobile nested instruction file**

Create `mobile/AGENTS.md` as a concise pointer to `docs/ai/14-flutter.md` and `docs/ai/05-api-conventions.md`, restating only the mobile boundary: Clean Architecture `presentation → domain ← data`, gateway-only HTTP, Vietnamese camelCase DTOs, Riverpod, SecureStorage, and no Java/Next.js rules.

- [ ] **Step 2: Verify nested coverage**

Confirm root `AGENTS.md` references the Flutter blueprint, `frontend/AGENTS.md` exists, and `mobile/AGENTS.md` exists. Add no duplicated business specification.

- [ ] **Step 3: Re-run the dev-kit audit**

Run `node scripts/sync-agent-devkit.mjs --check` and require exit code 0.

- [ ] **Step 4: Commit mobile instructions**

Commit `mobile/AGENTS.md` using `docs(tooling): add Codex mobile instructions`.

### Task 4: Update team onboarding and local-setting guidance

**Files:**
- Modify: `README.md`
- Modify: `docs/ai/13-codebase-tools.md`
- Modify: `CLAUDE.md`
- Modify: `.claude/commands/index-codebase.md`
- Modify: `scripts/bootstrap.ps1`
- Modify: `scripts/setup-codebase-memory.sh`
- Modify: `scripts/setup-codebase-memory.bat`

- [ ] **Step 1: Document both native adapter trees**

Update README diagrams and vendored-tool lists to include `.codex/agents`, `.codex/config.toml`, `.codex/hooks.json`, `.agents/skills`, `mobile/AGENTS.md`, and the sync audit command.

- [ ] **Step 2: Correct codebase-memory scope**

Replace statements that codebase-memory is Claude-only with Claude-and-Codex guidance. Show Claude `/index-codebase` and Codex `$index-codebase` or a natural-language request to run the skill.

- [ ] **Step 3: Document personal settings accurately**

State that Claude retains ignored `.claude/settings.local.json`; Codex personal settings belong in `~/.codex/config.toml`; no unsupported `.codex/settings.local.json` is created.

- [ ] **Step 4: Update setup messages**

Make bootstrap/setup output tell both Claude and Codex users to restart their client and index the repository after installation.

- [ ] **Step 5: Update Claude's index command note**

Keep the Claude command functional while removing the obsolete claim that Codex cannot use codebase-memory.

- [ ] **Step 6: Validate docs and commit**

Run `node scripts/sync-agent-devkit.mjs --check`, scan changed docs for obsolete `Claude-side`/`Codex users can skip` claims, run `git diff --check`, and commit using `docs(tooling): document dual-agent dev kit`.

### Task 5: Final verification and publication gate

**Files:**
- Verify all changed dev-kit files
- Preserve: `.claude/settings.local.json`
- Preserve unrelated application files

- [ ] **Step 1: Run fresh dev-kit verification**

Run generator check, JSON/TOML parsing, exact hook command comparison, both hook commands, MCP executable discovery, and codebase-memory MCP `index_status`/`get_architecture` calls.

- [ ] **Step 2: Run repository checks**

Run `mvn -q test`, `pnpm lint`, and `pnpm typecheck`. Record the already-known pharmacy ArchUnit and frontend effect-lint failures as pre-existing when the relevant files still match `origin/master`.

- [ ] **Step 3: Review repository state**

Confirm `.claude/settings.local.json` is ignored and unchanged, inspect every commit and diff against `origin/master`, ensure `.changelog/entries.jsonl` remains unstaged, and verify no secret or personal path was added.

- [ ] **Step 4: Push only after the scoped dev-kit checks pass**

Fetch `origin/master`, require a non-diverged history, push `master`, fetch again, and require local `HEAD` to equal `origin/master`. If an unrelated repository check still fails, report it explicitly with evidence before requesting or applying the user's publication decision.
