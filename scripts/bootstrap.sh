#!/usr/bin/env bash
# bootstrap.sh — one-time setup check for a new machine (macOS / Linux)
# Verifies prerequisites for the Claude Code toolkit + Spring Boot build.
# Does NOT install anything automatically — it reports what's missing (manual install by design).
set -euo pipefail

echo "==> Hospital MSA toolkit bootstrap (Unix)"

# 0. Git hooks
if [ -d "scripts/git-hooks" ]; then
  git config core.hooksPath scripts/git-hooks
  echo "[ok] Git hooks installed from scripts/git-hooks (single-author policy enforced)"
  if have node; then
    node scripts/changelog.js --init 2>/dev/null && echo "[ok] CHANGELOG.db initialized" || echo "[note] changelog init skipped (no sqlite3)"
  fi
else
  echo "[MISSING] scripts/git-hooks/ not found — hooks not configured"
fi

have() { command -v "$1" >/dev/null 2>&1; }

# 1. Java 21+
if have java; then echo "[ok] Java: $(java -version 2>&1 | head -1)"
else echo "[MISSING] Java 21 (LTS). Install a JDK 21."; fi

# 2. Maven (or wrapper)
if have mvn; then echo "[ok] Maven: $(mvn -v | head -1)"
elif [ -f "./mvnw" ]; then echo "[ok] Maven wrapper (mvnw) present"
else echo "[MISSING] Maven. Install Maven or add the Maven wrapper."; fi

# 3. Git
if have git; then echo "[ok] Git found"; else echo "[MISSING] Git."; fi

# 4. codebase-memory-mcp (optional for Claude and Codex)
if have codebase-memory-mcp; then
  echo "[ok] codebase-memory-mcp on PATH -> Claude and Codex project configs can launch it"
else
  echo "[note] codebase-memory-mcp NOT on PATH."
  echo "       Claude/Codex users: run scripts/setup-codebase-memory.sh and restart the client."
  echo "       The MCP is optional; agents fall back to docs and text search."
fi

echo ""
echo "Next steps:"
echo "  1) Read docs/ai/README.md (the coding standards)."
echo "  2) Build:  mvn -q -DskipTests install   (once the service modules exist)."
echo "  3) Init changelog: node scripts/changelog.js --init  (already done above)."
echo "  4) Restart Claude Code or Codex, then run /index-codebase or \$index-codebase."
echo "  5) Before coding, run: node scripts/changelog.js --summary"
