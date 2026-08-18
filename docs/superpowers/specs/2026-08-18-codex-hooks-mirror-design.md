# Codex Hooks Mirror Design

## Goal

Keep the existing Claude Code configuration unchanged while adding an independent Codex hook configuration that provides the same two MediFlow `SessionStart` actions.

## Scope

- Preserve `.claude/settings.json`, `.claude/settings.local.json`, and all other `.claude` content exactly as-is.
- Create `.codex/hooks.json` using Codex's supported project-local hook schema.
- Mirror only the two `SessionStart` command handlers currently defined in `.claude/settings.json`:
  1. Print the MediFlow backend, frontend, and optional tooling guidance.
  2. Run `node scripts/changelog.js --summary` from the active working directory and print its output.
- Do not copy Claude-specific permissions, marketplaces, local settings, agents, commands, or skills.

## Runtime Behavior

Claude Code continues loading `.claude/settings.json`. Codex independently discovers `.codex/hooks.json` when the repository is trusted. Codex requires review and trust of the hook definition before the commands run for the first time or after the hook definition changes.

The hook commands remain byte-for-byte equivalent to the Claude command strings so their observable behavior stays aligned across both agents.

## Validation

- Parse both JSON files successfully.
- Compare the `SessionStart` command arrays and require exact string equality.
- Execute each command directly from the repository root and require exit code 0.
- Confirm `.claude` has no diff after implementation.
- Confirm `.codex/hooks.json` is the only runtime configuration added for Codex.

## Known Constraint

Codex and Claude use different configuration discovery locations. Therefore, duplicating the entire Claude settings file would not provide a valid Codex configuration. The mirror preserves the hook commands exactly while wrapping them in Codex's supported `hooks.json` structure.
