#!/usr/bin/env sh
if ! command -v codebase-memory-mcp >/dev/null 2>&1; then
  echo "[FAIL] codebase-memory-mcp is not on PATH. Run scripts/setup-codebase-memory.sh." >&2
  exit 1
fi
exec codebase-memory-mcp "$@"
