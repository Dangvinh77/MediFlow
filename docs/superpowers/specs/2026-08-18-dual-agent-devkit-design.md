# Dual-Agent Dev Kit Design

## Goal

Provide one team-shared MediFlow development experience for Claude Code and Codex while preserving each tool's native configuration format and keeping machine-local settings private.

The result must let a developer clone the repository, install the documented optional tools, and receive equivalent project instructions, specialist agents, reusable workflows, lifecycle hooks, and MCP integrations in either Claude Code or Codex.

## Compatibility Principle

Functional equivalence is the target; byte-for-byte directory duplication is not. Claude Code and Codex discover different filenames and use different schemas. Existing `.claude` content remains available to Claude, while Codex-compatible adapters are committed in the locations Codex actually loads.

`.claude/settings.local.json` remains unchanged and ignored by Git. Codex does not load that filename, so each Codex user keeps personal settings in `~/.codex/config.toml`. Historical one-off command grants and absolute user paths from Claude local settings must not enter the shared repository.

## Component Mapping

| Capability | Claude source | Codex destination | Strategy |
| --- | --- | --- | --- |
| Repository instructions | `CLAUDE.md`, `AGENTS.md` | `AGENTS.md` | Continue using the existing cross-tool root instructions. |
| Frontend rules | `.claude/rules/frontend.md`, `frontend/AGENTS.md` | `frontend/AGENTS.md` | Keep the nested cross-tool file authoritative. |
| Flutter rules | `.claude/rules/flutter.md` | `mobile/AGENTS.md` | Add a nested Codex-compatible instruction file that points to `docs/ai/14-flutter.md`. |
| Specialist agents | `.claude/agents/*.md` | `.codex/agents/*.toml` | Convert frontmatter and bodies into native Codex agent definitions; preserve read-only versus write-capable behavior. |
| Slash commands | `.claude/commands/*.md` | `.agents/skills/<command>/SKILL.md` | Convert commands into shared Codex skills with equivalent descriptions, arguments, and steps. |
| New-service skill | `.claude/skills/new-microservice/SKILL.md` | `.agents/skills/new-microservice/SKILL.md` | Copy the tool-agnostic workflow and keep semantic parity. |
| Lifecycle hooks | `.claude/settings.json` | `.codex/hooks.json` | Retain the existing exact `SessionStart` command mirror. |
| Shared settings | `.claude/settings.json` | `.codex/config.toml` | Configure Codex features and project MCP servers using native TOML. |
| Machine-local settings | `.claude/settings.local.json` | `~/.codex/config.toml` | Document only; never commit personal allowlists or absolute paths. |
| MCP servers | `.mcp.json` | `.codex/config.toml` | Configure codebase-memory through the executable on `PATH` and Mermaid through its HTTP URL. |
| MCP launch helpers | `.claude/mcp-wrapper.*`, setup scripts | Existing setup scripts plus Codex config | Prefer the native executable on `PATH`; remove shared dependence on a specific Windows username. |
| Optional marketplace | `.claude/settings.json` | Codex setup documentation | Do not invent an incompatible Codex marketplace manifest. Document the verified Codex fallback: codebase-memory MCP and standard repository tools. |

## Shared Configuration

`.codex/config.toml` will be project-scoped and committed. It will:

- enable lifecycle hooks;
- configure `codebase-memory-mcp` as a stdio MCP command resolved from `PATH`;
- configure Mermaid Chart as an HTTP MCP server;
- avoid model selection, authentication, telemetry, and user-specific absolute paths;
- avoid weakening a developer's personal sandbox or approval policy.

The existing `.mcp.json` contains a hard-coded path for another Windows user. It will be changed to invoke the native `codebase-memory-mcp` executable from `PATH`, matching the repository setup scripts. Claude and other `.mcp.json` consumers will therefore use the same portable installation contract as Codex.

## Agents

Four project-scoped Codex agents will mirror the Claude agents:

- `code-reviewer`: read-only reviewer with the existing standards checklist;
- `java-architect`: read-only architecture adviser grounded in the graph and design documentation;
- `spring-boot-engineer`: workspace-write implementation agent;
- `test-engineer`: workspace-write testing agent.

Each Codex agent TOML will contain `name`, `description`, `sandbox_mode`, and `developer_instructions`. Agent-specific MCP tool name lists from Claude will not be copied as unsupported syntax; the agents inherit project MCP configuration and their sandbox mode provides the enforceable read/write boundary.

## Commands and Skills

The three Claude commands become repository skills:

- `index-codebase`;
- `new-service`;
- `review-pr`.

The existing `new-microservice` skill is also mirrored. Skill descriptions will preserve their activation intent, and their bodies will use Codex-compatible tool language without changing business rules.

The stale statement that codebase-memory is Claude-only will be removed from the Codex mirror. Graph discovery remains optional: use MCP graph tools when available and fall back to repository files and text search otherwise.

## Rules and Instruction Loading

Root `AGENTS.md` remains the shared entry point. `frontend/AGENTS.md` already supplies the nested frontend rules. A new `mobile/AGENTS.md` will provide the equivalent nested Flutter scope.

Codex determines nested instructions from the task working directory rather than from every file touched. Root `AGENTS.md` already instructs agents to read the mobile and frontend blueprints, so the nested files strengthen specialized sessions without being the sole enforcement mechanism.

## Synchronization Guard

A cross-platform Node.js audit script will compare the dual adapters at the semantic level:

- Claude and Codex hook command arrays must match exactly;
- every Claude agent must have a Codex TOML counterpart;
- every Claude command must have a Codex skill counterpart;
- the `new-microservice` skill must exist on both sides;
- frontend and mobile cross-tool rule entry points must exist;
- MCP server names and endpoints must match across `.mcp.json` and `.codex/config.toml`;
- committed files must not contain the known user-specific MCP path.

The audit reports actionable mismatches and exits nonzero. It will be documented in the repository onboarding instructions and can be run locally or added to CI later without requiring either Claude or Codex to be installed.

## Validation

Implementation is acceptable only when all of these checks pass:

1. Parse Claude JSON, Codex JSON, and Codex TOML successfully.
2. Run the synchronization audit with exit code 0.
3. Execute both mirrored hook commands successfully from the repository root.
4. Confirm `codebase-memory-mcp` is discoverable on `PATH` on the current machine and its MCP tools remain callable.
5. Confirm all Codex agents and skills appear in the expected project directories with valid required metadata.
6. Confirm `.claude/settings.local.json` remains ignored and unchanged.
7. Run repository tests and lint checks, recording unrelated pre-existing failures separately from dev-kit verification.
8. Review the final Git diff to ensure no secrets, personal paths, or unrelated application changes are included.

## Documentation

Update the AI tooling documentation and onboarding text to explain:

- which directories are native to Claude and Codex;
- how developers install codebase-memory MCP;
- how to index the repository;
- where personal settings belong for each tool;
- how to run the synchronization audit;
- that project hooks require trust/review on first use.

## Out of Scope

- Copying personal Claude permission history into the repository.
- Selecting or enforcing a Codex model for the team.
- Installing plugins or changing user accounts automatically.
- Fixing unrelated Maven or frontend lint failures already present on `origin/master`.
