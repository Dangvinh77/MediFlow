#!/usr/bin/env bash
# setup-hooks.sh — enable the MediFlow git hooks on this machine (macOS / Linux)
#
# The hook scripts are committed at scripts/git-hooks/, but git only runs them
# when core.hooksPath points there — and that setting lives in .git/config,
# which is LOCAL to each machine and NEVER travels with `git clone`.
# Run this once after cloning (bootstrap.sh does it automatically).
#
# Usage:
#   bash scripts/setup-hooks.sh
set -euo pipefail

if [ ! -d "scripts/git-hooks" ]; then
  echo "[ERROR] scripts/git-hooks/ not found — run this from the repo root." >&2
  exit 1
fi

git config core.hooksPath scripts/git-hooks

echo "[ok] Git hooks enabled: core.hooksPath = scripts/git-hooks"
echo "     - prepare-commit-msg strips AI attribution trailers"
echo "     - commit-msg allows exactly the 4 contributors in scripts/allowed-contributors.json"
echo "     - post-commit / post-merge maintain the changelog (.changelog/)"

HP="$(git config --get core.hooksPath)"
echo "Active hooksPath: ${HP}"
