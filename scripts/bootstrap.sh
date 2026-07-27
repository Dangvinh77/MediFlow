#!/usr/bin/env bash
# bootstrap.sh — one-time setup check for a new machine (macOS / Linux)
# Verifies prerequisites for the Claude Code toolkit + Spring Boot build.
# Does NOT install anything automatically — it reports what's missing (manual install by design).
set -euo pipefail

echo "==> Hospital MSA toolkit bootstrap (Unix)"

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

# 4. codebase-memory-mcp (Claude users only)
if have codebase-memory-mcp; then
  echo "[ok] codebase-memory-mcp on PATH -> .mcp.json works as-is"
else
  echo "[note] codebase-memory-mcp NOT on PATH."
  echo "       Claude users: install it, then add it to PATH, or copy"
  echo "       .mcp.local.json.example -> .mcp.local.json with the absolute path."
  echo "       Codex/Cursor users can ignore this (they use docs/ai/ directly)."
fi

echo ""
echo "Next steps:"
echo "  1) Read docs/ai/README.md (the coding standards)."
echo "  2) Build:  mvn -q -DskipTests install   (once the service modules exist)."
echo "  3) Claude users: open the repo in Claude Code and run /index-codebase to build the graph."
