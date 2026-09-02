---
name: "source-command-index-codebase"
description: "Index (or re-index) this repo into codebase-memory-mcp so graph tools work on this machine."
---

# source-command-index-codebase

Use this skill when the user asks to run the migrated source command `index-codebase`.

## Command Template

Index the current repository into the codebase knowledge graph using codebase-memory-mcp.

Steps:
1. Call `list_projects` and `index_status` to check whether this repo is already indexed and fresh.
2. If not indexed or stale, call `index_repository` with `repo_path` = the repo root (`E:/DEV/Coding_Resource/Project/e_PROJECT/Semester4/Source` or the current working directory). Use `mode: "full"` for the first index; `moderate` or `fast` for quick refreshes.
3. If the team wants to share the graph so others skip a full re-index, pass `persistence: true` to write `.codebase-memory/graph.db.zst` (see README — that artifact can optionally be committed).
4. After indexing, confirm with `get_architecture` that the services show up, and report a short summary (modules, clusters) to the user.

The MCP is optional for both Codex and Codex. When unavailable, fall back to repository documentation and text search.
