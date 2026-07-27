# bootstrap.ps1 — one-time setup check for a new machine (Windows / PowerShell)
# Verifies prerequisites for the Claude Code toolkit + Spring Boot build.
# Does NOT install anything automatically — it reports what's missing (manual install by design).

$ErrorActionPreference = 'Stop'
Write-Host "==> Hospital MSA toolkit bootstrap (Windows)" -ForegroundColor Cyan

function Test-Cmd($name) { return [bool](Get-Command $name -ErrorAction SilentlyContinue) }

# 1. Java 21+
if (Test-Cmd java) {
  $v = (java -version 2>&1 | Select-Object -First 1)
  Write-Host "[ok] Java found: $v"
} else { Write-Host "[MISSING] Java 21 (LTS). Install a JDK 21." -ForegroundColor Yellow }

# 2. Maven (or wrapper)
if (Test-Cmd mvn) { Write-Host "[ok] Maven found: $((mvn -v | Select-Object -First 1))" }
elseif (Test-Path ".\mvnw.cmd") { Write-Host "[ok] Maven wrapper (mvnw.cmd) present" }
else { Write-Host "[MISSING] Maven. Install Maven or add the Maven wrapper." -ForegroundColor Yellow }

# 3. Git
if (Test-Cmd git) { Write-Host "[ok] Git found" } else { Write-Host "[MISSING] Git." -ForegroundColor Yellow }

# 4. codebase-memory-mcp (Claude users only)
if (Test-Cmd codebase-memory-mcp) {
  Write-Host "[ok] codebase-memory-mcp on PATH -> .mcp.json will work as-is"
} else {
  Write-Host "[note] codebase-memory-mcp NOT on PATH." -ForegroundColor Yellow
  Write-Host "       Claude users: install it, then either add its folder to PATH," -ForegroundColor Yellow
  Write-Host "       or copy .mcp.local.json.example -> .mcp.local.json and set the absolute path." -ForegroundColor Yellow
  Write-Host "       Codex/Cursor users can ignore this (they use docs/ai/ directly)." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  1) Read docs/ai/README.md (the coding standards)."
Write-Host "  2) Build:  mvn -q -DskipTests install   (once the service modules exist)."
Write-Host "  3) Claude users: open this repo in Claude Code and run /index-codebase to build the graph."
Write-Host "     (Or ask Claude to run index_repository on the repo root.)"
