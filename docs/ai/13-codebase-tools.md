# 13 — AI Codebase Tools (Optional)

MediFlow ships with **two optional AI code-intelligence tools**. Each developer installs them once per machine; the project config makes them discoverable to everyone who clones the repo.

| Tool | Type | What it does | Token cost |
|------|------|-------------|------------|
| **[codebase-memory-mcp](#codebase-memory-mcp)** | Native binary MCP server | Sub-ms structural queries (call chains, imports, HTTP routes). Used by AI *agents* during coding. | **Zero** (deterministic) |
| **[Understand-Anything](#understand-anything)** | Claude Code plugin | Interactive visual dashboard + guided tours. Used by *humans* to explore the codebase. | **High** first run (LLM agents), low on incremental runs |

> **Both are optional.** If a tool isn't installed, Grep/Glob/Read work fine — just slower for structural queries, and no dashboard for visual exploration.

---

## codebase-memory-mcp

### What it gives you

15 MCP tools that AI agents use to navigate code at graph speed:

| Tool | Example use |
|------|------------|
| `search_graph(name_pattern=".*Repository.*")` | Find all repository classes |
| `trace_path(function_name="handleCreatePatient")` | Trace a method's callers/callees |
| `get_code_snippet(qualified_name="...")` | Read exact symbol source |
| `query_graph(MATCH ... RETURN ...)` | Custom Cypher queries |
| `get_architecture(aspects=[...])` | Project structure overview |
| `detect_changes()` | Map git diff to affected symbols |

### Install

```bash
# Option A: Run the setup script
scripts/setup-codebase-memory.sh          # macOS / Linux
scripts\setup-codebase-memory.bat         # Windows (Git Bash / cmd)

# Option B: Manual download
# 1. Go to https://github.com/DeusData/codebase-memory-mcp/releases/latest
# 2. Download <platform>-amd64 binary
# 3. Place it in a directory on your PATH (~/.local/bin/ or equivalent)
# 4. Verify:  codebase-memory-mcp --version
```

### Post-install

```bash
# 1. Restart Claude Code so .mcp.json picks up the binary
# 2. Build the graph:
/index-codebase

# The first index takes ~30s–3min for MediFlow. Subsequent runs are fast incremental updates.
# If you want to share the index artifact so others skip a full re-index:
#   /index-codebase with persistence: true → writes .codebase-memory/graph.db.zst
#   Then commit the .zst file (uncomment the gitignore exception)
```

### How agents use it

When the binary is installed and the graph is indexed, Claude Code agents **automatically prefer** graph tools over Grep/Glob/Read. You don't need to do anything special — it just works.

If you see "mcp__codebase-memory-mcp__* tools not found", the binary isn't on PATH. Check with `which codebase-memory-mcp`.

### Override the binary path (if not on PATH)

Copy `.mcp.local.json.example` to `.mcp.local.json` and set the absolute path to your binary. This file is gitignored — safe for machine-specific paths.

---

## Understand-Anything

### What it gives you

7 slash commands for visual codebase exploration:

| Command | What it does |
|---------|-------------|
| `/understand` | Full analysis → builds knowledge graph (first run) |
| `/understand-dashboard` | Open interactive graph dashboard |
| `/understand-chat How does payment work?` | Ask questions about the codebase |
| `/understand-diff` | See ripple effects of your changes |
| `/understand-explain src/...` | Deep-dive into a specific file/function |
| `/understand-domain` | Business domain view (flows, processes) |
| `/understand-onboard` | Generate onboarding guide for new devs |

### Install

```bash
# Option A: Run the setup script
scripts/setup-understand-anything.sh          # macOS / Linux
scripts\setup-understand-anything.bat         # Windows

# Option B: Manual (inside Claude Code)
/plugin marketplace add Egonex-AI/Understand-Anything
/plugin install understand-anything
```

### First run

```bash
/understand
```

This analyzes the full codebase via a multi-agent pipeline (tree-sitter parsing + LLM summarization). It takes ~2–5 minutes for MediFlow and **consumes tokens** on first run. Subsequent runs are **incremental** (only changed files).

When done, it auto-opens the dashboard at `localhost:5173`.

### Tips

- **Language**: `/understand --language vi` for Vietnamese descriptions
- **Scoped analysis**: `/understand src/frontend` for just the frontend
- **Auto-update**: `/understand --auto-update` runs on every commit
- **Token concern?** Skip the first `/understand` and just use `/understand-dashboard` if someone on the team already generated the knowledge graph (it's in `.ua/knowledge-graph.json`)

---

## Quick setup (both tools)

```bash
# Install everything at once:
scripts\setup-tools.bat                      # Windows (both tools)
scripts/setup-tools.sh                       # macOS / Linux (both tools)

# Or just codebase-memory-mcp (no token cost):
scripts\setup-tools.bat --minimal            # Windows
scripts/setup-tools.sh --minimal             # macOS / Linux
```

---

## Comparison: when to use which

| Scenario | Which tool |
|----------|-----------|
| "What calls `createPatient()`?" | **codebase-memory-mcp** — `trace_path` in <1ms, zero tokens |
| "I'm new, show me the big picture" | **Understand-Anything** — `/understand-dashboard` with guided tours |
| "What does this service do?" | **Understand-Anything** — `/understand-explain patient-service/` |
| "Find dead code / unused imports" | **codebase-memory-mcp** — `search_graph(max_degree=0)` |
| "How does the billing saga work?" | **Understand-Anything** — `/understand-chat How does billing work?` |
| "Who depends on this class?" | **codebase-memory-mcp** — `trace_path` inbound |
| "Onboarding a new team member" | **Understand-Anything** — `/understand-onboard` |
| "I'm actively coding, need fast lookups" | **codebase-memory-mcp** — agents use it automatically |
