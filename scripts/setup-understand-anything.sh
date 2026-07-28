#!/usr/bin/env bash
# setup-understand-anything.sh — Register + install Understand-Anything plugin
#
# Run ONCE on each developer machine after cloning the repo.
# Requires: Claude Code CLI on PATH.
#
# Usage:
#   ./scripts/setup-understand-anything.sh
#
# After installing:
#   - Run  /understand  to analyze the codebase (takes ~2-5 min, uses tokens)
#   - Run  /understand-dashboard  to explore the visual graph
#
# NOTE: The first /understand run uses significant tokens (LLM analysis).
#       Subsequent runs are incremental (only changed files).

set -euo pipefail

echo "[..] Adding Understand-Anything marketplace..."

if ! claude plugin marketplace add Egonex-AI/Understand-Anything 2>/dev/null; then
  echo "[FAIL] Could not register marketplace. Is Claude Code on PATH?"
  echo "       Try manually in Claude Code:  /plugin marketplace add Egonex-AI/Understand-Anything"
  exit 1
fi

echo "[..] Installing Understand-Anything plugin..."

if ! claude plugin install understand-anything 2>/dev/null; then
  echo "[FAIL] Plugin install failed."
  echo "       Try manually in Claude Code:  /plugin install understand-anything"
  exit 1
fi

echo ""
echo "============================================="
echo "  Plugin installed!"
echo "============================================="
echo "  Commands:"
echo "    /understand              Analyze codebase"
echo "    /understand-dashboard    Open visual graph"
echo "    /understand-chat         Ask about code"
echo "    /understand-diff         Impact analysis"
echo "    /understand-explain      Deep-dive into file"
echo "    /understand-domain       Business domain view"
echo "    /understand-onboard      Onboarding guide"
echo ""
echo "  First run:  /understand"
echo "  (will auto-open dashboard when done)"
echo "============================================="
