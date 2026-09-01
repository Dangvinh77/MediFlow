#!/usr/bin/env bash
# setup-codebase-memory.sh — Download and install codebase-memory-mcp binary
#
# Run once per dev machine after cloning. Works on macOS, Linux, and Git Bash.
# Requires: curl, install dir in PATH (default: ~/.local/bin)
#
# Usage:
#   ./scripts/setup-codebase-memory.sh                  # auto-detect OS/arch
#   ./scripts/setup-codebase-memory.sh /custom/path     # custom install dir
#
# After installing, restart Claude Code or Codex, then run /index-codebase
# or $index-codebase to build the knowledge graph.

set -euo pipefail

INSTALL_DIR="${1:-$HOME/.local/bin}"
RELEASES_URL="https://github.com/DeusData/codebase-memory-mcp/releases/latest/download"

# Auto-detect OS and architecture
OS=$(uname -s | tr '[:upper:]' '[:lower:]')
ARCH=$(uname -m)

case "$OS" in
  linux)   PLATFORM="linux" ;;
  darwin)  PLATFORM="darwin" ;;
  mingw*|msys*|cygwin*) PLATFORM="windows" ;;
  *)
    echo "[FAIL] Unsupported OS: $OS"
    exit 1
    ;;
esac

case "$ARCH" in
  x86_64|amd64) ARCH_SUFFIX="amd64" ;;
  aarch64|arm64) ARCH_SUFFIX="arm64" ;;
  *)
    echo "[FAIL] Unsupported architecture: $ARCH"
    exit 1
    ;;
esac

BINARY="codebase-memory-mcp-${PLATFORM}-${ARCH_SUFFIX}"
if [ "$PLATFORM" = "windows" ]; then
  BINARY="${BINARY}.exe"
fi

DEST="${INSTALL_DIR}/codebase-memory-mcp${PLATFORM:+.exe}"
PLATFORM_SUFFIX=""
[ "$PLATFORM" = "windows" ] && PLATFORM_SUFFIX=".exe"

# Detect correct dest name (the binary name on PATH should be just "codebase-memory-mcp")
DEST="${INSTALL_DIR}/codebase-memory-mcp${PLATFORM_SUFFIX}"

echo "[..] Platform: ${PLATFORM}-${ARCH_SUFFIX}"

# Check if already installed and on PATH
if command -v codebase-memory-mcp &>/dev/null; then
  echo "[OK] codebase-memory-mcp already on PATH: $(command -v codebase-memory-mcp)"
  echo "[..] Version: $(codebase-memory-mcp --version 2>/dev/null || codebase-memory-mcp version 2>/dev/null || echo 'unknown')"
  exit 0
fi

if [ -f "$DEST" ]; then
  echo "[OK] codebase-memory-mcp found at $DEST"
  exit 0
fi

echo "[..] Downloading codebase-memory-mcp ${BINARY}..."

mkdir -p "$INSTALL_DIR"

# Download
URL="${RELEASES_URL}/${BINARY}"
echo "[..] URL: $URL"
curl -fsSL -o "$DEST" "$URL"
chmod +x "$DEST"

echo "[OK] Installed to $DEST"

# Suggest PATH fix if needed
case ":$PATH:" in
  *:"$INSTALL_DIR":*) ;;
  *)
    echo ""
    echo "[WARN] $INSTALL_DIR is not in your PATH."
    echo "       Add this to your shell profile (~/.bashrc, ~/.zshrc, etc.):"
    echo "       export PATH=\"\$PATH:$INSTALL_DIR\""
    ;;
esac

echo ""
echo "============================================="
echo "  Setup complete!"
echo "============================================="
echo "  1. Restart Claude Code or Codex"
echo "  2. Run /index-codebase (Claude) or \$index-codebase (Codex)"
echo "     (first index ~1-3 min for MediFlow)"
echo "  3. Start coding — agents use graph tools"
echo "============================================="
