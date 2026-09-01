# Codex Hooks Mirror Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a project-local Codex `SessionStart` hook mirror without changing the existing Claude Code configuration.

**Architecture:** Claude Code remains configured through `.claude/settings.json`. Codex independently loads `.codex/hooks.json`; its two command handlers must exactly equal the two Claude `SessionStart` command strings.

**Tech Stack:** JSON, Node.js, PowerShell, Git, Codex lifecycle hooks

---

### Task 1: Add the Codex hook mirror

**Files:**
- Create: `.codex/hooks.json`
- Preserve: `.claude/settings.json`

- [ ] **Step 1: Confirm the mirror does not exist yet**

Run:

```powershell
if (Test-Path -LiteralPath '.codex/hooks.json') { throw '.codex/hooks.json already exists' }
```

Expected: exit code 0 with no output.

- [ ] **Step 2: Create the Codex hook file**

Create `.codex/hooks.json` with this exact content:

```json
{
  "description": "Mirror of MediFlow SessionStart hooks from .claude/settings.json for Codex.",
  "hooks": {
    "SessionStart": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "node -e \"console.log('[MediFlow] BACKEND: docs/ai/04-microservice-blueprint.md (clean architecture). FRONTEND: docs/ai/12-frontend.md. OPTIONAL: docs/ai/13-codebase-tools.md (codebase-memory-mcp + Understand-Anything).')\""
          },
          {
            "type": "command",
            "command": "node -e \"var s=require('child_process').execSync('node scripts/changelog.js --summary 2>&1',{cwd:process.cwd()}); console.log(s.toString().trim())\""
          }
        ]
      }
    ]
  }
}
```

- [ ] **Step 3: Parse both JSON files and compare commands**

Run:

```powershell
$claude = Get-Content -Raw '.claude/settings.json' | ConvertFrom-Json
$codex = Get-Content -Raw '.codex/hooks.json' | ConvertFrom-Json
$claudeCommands = @($claude.hooks.SessionStart[0].hooks | ForEach-Object command)
$codexCommands = @($codex.hooks.SessionStart[0].hooks | ForEach-Object command)
if ((Compare-Object $claudeCommands $codexCommands -SyncWindow 0) -or $claudeCommands.Count -ne 2) { throw 'Hook commands differ' }
```

Expected: exit code 0 with no output.

- [ ] **Step 4: Execute both mirrored commands**

Run each value from `$codexCommands` with `Invoke-Expression` from the repository root.

Expected: the first prints the MediFlow guidance; the second prints the changelog summary; both exit with code 0.

- [ ] **Step 5: Verify Claude configuration is untouched**

Run:

```powershell
git diff --exit-code -- .claude
git diff --check -- .codex/hooks.json
```

Expected: both commands exit with code 0.

- [ ] **Step 6: Commit the mirror**

```powershell
git add -- .codex/hooks.json
git commit -m "chore(tooling): mirror Claude hooks for Codex"
```

Expected: one commit containing only `.codex/hooks.json`.

### Task 2: Verify and publish

**Files:**
- Verify: `.codex/hooks.json`
- Preserve: `.claude/settings.json`

- [ ] **Step 1: Re-run JSON, equality, command, and diff checks**

Run the checks from Task 1 Steps 3-5 again.

Expected: every check exits with code 0.

- [ ] **Step 2: Confirm branch and remote relationship**

Run:

```powershell
git fetch --prune origin
git rev-list --left-right --count HEAD...origin/master
git status --short --branch
```

Expected: local `master` is ahead only by the design, plan, and implementation commits; `.claude` has no changes.

- [ ] **Step 3: Push the verified commits**

```powershell
git push origin master
```

Expected: remote `master` advances to the local implementation commit.

- [ ] **Step 4: Confirm remote commit equality**

Run:

```powershell
git fetch origin master
if ((git rev-parse HEAD) -ne (git rev-parse origin/master)) { throw 'Remote is not synchronized' }
```

Expected: exit code 0.
